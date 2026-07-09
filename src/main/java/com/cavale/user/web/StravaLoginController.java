package com.cavale.user.web;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cavale.integration.strava.StravaAuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Public: "Continuer avec Strava" — sign in (or up) with a Strava account. */
@RestController
@RequestMapping("/api/auth/strava")
@Tag(name = "Auth", description = "Registration and authentication")
public class StravaLoginController {

    private final StravaAuthService stravaAuthService;

    public StravaLoginController(StravaAuthService stravaAuthService) {
        this.stravaAuthService = stravaAuthService;
    }

    @GetMapping("/login-url")
    @Operation(summary = "URL to sign in with Strava (creates the account on first login)")
    public Map<String, String> loginUrl() {
        return Map.of("url", stravaAuthService.loginUrl());
    }
}
