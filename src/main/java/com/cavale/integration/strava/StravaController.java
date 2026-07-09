package com.cavale.integration.strava;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/strava")
@Tag(name = "Strava", description = "Strava connection and activity sync")
public class StravaController {

    private final StravaAuthService authService;
    private final StravaSyncService syncService;
    private final StravaConnectionRepository connectionRepository;
    private final StravaProperties properties;

    public StravaController(StravaAuthService authService, StravaSyncService syncService,
                            StravaConnectionRepository connectionRepository, StravaProperties properties) {
        this.authService = authService;
        this.syncService = syncService;
        this.connectionRepository = connectionRepository;
        this.properties = properties;
    }

    public record StravaStatus(boolean configured, boolean connected, Long athleteId, Instant lastSyncAt) {
    }

    @GetMapping("/status")
    @Operation(summary = "Connection status for the current user")
    public StravaStatus status(@AuthenticationPrincipal Jwt jwt) {
        return connectionRepository.findByUserId(userId(jwt))
                .map(c -> new StravaStatus(properties.configured(), true, c.getAthleteId(), c.getLastSyncAt()))
                .orElseGet(() -> new StravaStatus(properties.configured(), false, null, null));
    }

    @GetMapping("/authorize-url")
    @Operation(summary = "URL to send the user to Strava's consent screen")
    public Map<String, String> authorizeUrl(@AuthenticationPrincipal Jwt jwt) {
        return Map.of("url", authService.authorizeUrl(userId(jwt)));
    }

    /** Browser redirect from strava.com — authenticated via the signed state, not a bearer token. */
    @GetMapping("/callback")
    @Operation(summary = "OAuth callback (redirects back to the web app)")
    public ResponseEntity<Void> callback(@RequestParam(required = false) String code,
                                         @RequestParam(required = false) String state,
                                         @RequestParam(required = false) String error) {
        String redirect = authService.handleCallback(code, state, error);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(redirect)).build();
    }

    @PostMapping("/sync")
    @Operation(summary = "Import recent Strava runs and validate matching sessions")
    public StravaSyncService.SyncResult sync(@AuthenticationPrincipal Jwt jwt) {
        return syncService.sync(userId(jwt));
    }

    @DeleteMapping("/connection")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Disconnect Strava")
    public void disconnect(@AuthenticationPrincipal Jwt jwt) {
        authService.disconnect(userId(jwt));
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
