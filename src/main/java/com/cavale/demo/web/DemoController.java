package com.cavale.demo.web;

import java.time.LocalDate;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cavale.demo.DemoProperties;
import com.cavale.demo.DemoService;
import com.cavale.user.domain.User;
import com.cavale.user.dto.AuthResponse;
import com.cavale.user.dto.UserResponse;
import com.cavale.user.service.TokenService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Public entry point for the portfolio demo. One POST spins up a private,
 * fully-seeded, throwaway sandbox and returns a short-lived session — no
 * registration, and nothing to clean up (the account self-destructs).
 */
@RestController
@RequestMapping("/api/auth/demo")
@Tag(name = "Auth", description = "Registration and authentication")
public class DemoController {

    private final DemoService demoService;
    private final TokenService tokenService;
    private final DemoProperties properties;

    public DemoController(DemoService demoService, TokenService tokenService, DemoProperties properties) {
        this.demoService = demoService;
        this.tokenService = tokenService;
        this.properties = properties;
    }

    @PostMapping
    @Operation(summary = "Start a throwaway, fully-seeded demo session (no sign-up)")
    public ResponseEntity<AuthResponse> startDemo() {
        User user = demoService.provision(LocalDate.now());
        String token = tokenService.issueDemoToken(user, properties.tokenTtl());
        return ResponseEntity.ok(new AuthResponse(token, UserResponse.from(user)));
    }
}
