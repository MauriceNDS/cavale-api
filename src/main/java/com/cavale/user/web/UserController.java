package com.cavale.user.web;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.cavale.user.dto.UpdateProfileRequest;
import com.cavale.user.dto.UpdateStatusRequest;
import com.cavale.user.dto.UserResponse;
import com.cavale.user.service.TokenService;
import com.cavale.user.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/users")
@Tag(name = "Users", description = "User account operations")
public class UserController {

    private final UserService userService;
    private final TokenService tokenService;

    public UserController(UserService userService, TokenService tokenService) {
        this.userService = userService;
        this.tokenService = tokenService;
    }

    @GetMapping("/me")
    @Operation(summary = "Get the authenticated user's profile")
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return UserResponse.from(userService.getById(userId));
    }

    @PutMapping("/me/profile")
    @Operation(summary = "Replace the athlete profile (name, weight, height, birth date, HR)")
    public UserResponse updateProfile(@AuthenticationPrincipal Jwt jwt,
                                      @Valid @RequestBody UpdateProfileRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return UserResponse.from(userService.updateProfile(userId, request));
    }

    @PutMapping("/me/status")
    @Operation(summary = "Set the athlete's availability (injured, sick, recovering…)")
    public UserResponse updateStatus(@AuthenticationPrincipal Jwt jwt,
                                     @Valid @RequestBody UpdateStatusRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return UserResponse.from(userService.updateStatus(userId, request));
    }

    @PostMapping("/me/pat")
    @Operation(summary = "Issue a long-lived personal access token (MCP client credential). "
            + "Shown once — store it in the MCP client configuration.")
    public TokenService.IssuedToken issuePat(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return tokenService.issuePersonalToken(userService.getById(userId));
    }

    @PostMapping("/me/revoke-tokens")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Revoke every token for this account — all sessions and "
            + "personal access tokens, including the current one. Use if a PAT leaks.")
    public void revokeTokens(@AuthenticationPrincipal Jwt jwt) {
        userService.revokeTokens(UUID.fromString(jwt.getSubject()));
    }
}
