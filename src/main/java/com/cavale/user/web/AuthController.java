package com.cavale.user.web;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import com.cavale.user.domain.User;
import com.cavale.user.dto.AuthResponse;
import com.cavale.user.dto.LoginRequest;
import com.cavale.user.dto.RegisterRequest;
import com.cavale.user.dto.UserResponse;
import com.cavale.user.service.InvalidRefreshTokenException;
import com.cavale.user.service.RefreshTokenService;
import com.cavale.user.service.TokenService;
import com.cavale.user.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "Registration and authentication")
public class AuthController {

    private final UserService userService;
    private final TokenService tokenService;
    private final RefreshTokenService refreshTokenService;
    private final RefreshCookie refreshCookie;

    public AuthController(UserService userService, TokenService tokenService,
                          RefreshTokenService refreshTokenService, RefreshCookie refreshCookie) {
        this.userService = userService;
        this.tokenService = tokenService;
        this.refreshTokenService = refreshTokenService;
        this.refreshCookie = refreshCookie;
    }

    @PostMapping("/register")
    @Operation(summary = "Register a new user account")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Account created"),
            @ApiResponse(responseCode = "400", description = "Invalid input"),
            @ApiResponse(responseCode = "409", description = "Email already in use")
    })
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request,
                                                 UriComponentsBuilder uriBuilder) {
        User user = userService.register(request.email(), request.password(), request.displayName());
        URI location = uriBuilder.path("/api/users/{id}").buildAndExpand(user.getId()).toUri();
        return ResponseEntity.created(location).body(UserResponse.from(user));
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate and receive a JWT")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Authenticated"),
            @ApiResponse(responseCode = "401", description = "Invalid credentials")
    })
    public AuthResponse login(@Valid @RequestBody LoginRequest request,
                              HttpServletResponse response) {
        User user = userService.authenticate(request.email(), request.password());
        refreshCookie.set(response, refreshTokenService.issueFor(user.getId()));
        return new AuthResponse(tokenService.issueFor(user), UserResponse.from(user));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Trade the refresh cookie for a fresh access token")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Renewed"),
            @ApiResponse(responseCode = "401", description = "No usable refresh token")
    })
    public AuthResponse refresh(HttpServletRequest request, HttpServletResponse response) {
        String presented = refreshCookie.read(request);
        if (presented == null) {
            refreshCookie.clear(response);
            throw new InvalidRefreshTokenException("No refresh cookie");
        }
        try {
            RefreshTokenService.Rotated rotated = refreshTokenService.rotate(presented);
            refreshCookie.set(response, rotated.refresh());
            return new AuthResponse(tokenService.issueFor(rotated.user()),
                    UserResponse.from(rotated.user()));
        } catch (InvalidRefreshTokenException e) {
            // Never leave a dead cookie behind — the client would retry it forever.
            refreshCookie.clear(response);
            throw e;
        }
    }

    @PostMapping("/logout")
    @Operation(summary = "Sign this device out")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        String presented = refreshCookie.read(request);
        if (presented != null) {
            refreshTokenService.revoke(presented);
        }
        refreshCookie.clear(response);
        return ResponseEntity.noContent().build();
    }
}
