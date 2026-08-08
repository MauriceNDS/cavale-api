package com.cavale.user.web;

import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cavale.user.config.DevLoginProperties;
import com.cavale.user.domain.User;
import com.cavale.user.dto.AuthResponse;
import com.cavale.user.dto.UserResponse;
import com.cavale.user.repository.UserRepository;
import com.cavale.user.service.InvalidCredentialsException;
import com.cavale.user.service.RefreshTokenService;
import com.cavale.user.service.TokenService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/**
 * Dev-only door: exchange a known email for a session, no password, no
 * Strava. Guarded by {@code cavale.dev-login.enabled} (default false); when
 * disabled the endpoints behave as if the account didn't exist.
 */
@RestController
@RequestMapping("/api/auth/dev-login")
@Tag(name = "Auth", description = "Registration and authentication")
public class DevLoginController {

    private static final Logger log = LoggerFactory.getLogger(DevLoginController.class);

    private final DevLoginProperties properties;
    private final UserRepository userRepository;
    private final TokenService tokenService;
    private final RefreshTokenService refreshTokenService;
    private final RefreshCookie refreshCookie;

    public DevLoginController(DevLoginProperties properties, UserRepository userRepository,
                              TokenService tokenService, RefreshTokenService refreshTokenService,
                              RefreshCookie refreshCookie) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.tokenService = tokenService;
        this.refreshTokenService = refreshTokenService;
        this.refreshCookie = refreshCookie;
    }

    @PostConstruct
    void warnIfEnabled() {
        if (properties.enabled()) {
            log.warn("DEV LOGIN DOOR ENABLED — any known email signs in without a password. "
                    + "Never enable this on a publicly reachable deployment.");
        }
    }

    public record DevLoginRequest(@NotBlank String email) {
    }

    @GetMapping
    @Operation(summary = "Whether the passwordless dev door is open on this deployment")
    public Map<String, Boolean> enabled() {
        return Map.of("enabled", properties.enabled());
    }

    @PostMapping
    @Operation(summary = "Dev only: authenticate by email, no password")
    public AuthResponse login(@Valid @RequestBody DevLoginRequest request,
                              HttpServletResponse response) {
        if (!properties.enabled()) {
            throw new InvalidCredentialsException();
        }
        User user = userRepository.findByEmail(request.email().trim().toLowerCase(Locale.ROOT))
                .filter(u -> !u.isDemo())
                .orElseThrow(InvalidCredentialsException::new);
        log.info("Dev login for {}", user.getEmail());
        refreshCookie.set(response, refreshTokenService.issueFor(user.getId()));
        return new AuthResponse(tokenService.issueFor(user), UserResponse.from(user));
    }
}
