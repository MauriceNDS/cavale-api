package com.cavale.integration.strava;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Strava OAuth: the authorize URL carries a short-lived signed state so the
 * callback (which arrives WITHOUT our bearer token — it's a browser redirect
 * from strava.com) can still be tied to the right user, CSRF-safely.
 */
@Service
public class StravaAuthService {

    private static final String STATE_PURPOSE = "strava-oauth";

    private final StravaProperties properties;
    private final StravaClient stravaClient;
    private final StravaConnectionRepository connectionRepository;
    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;

    public StravaAuthService(StravaProperties properties, StravaClient stravaClient,
                             StravaConnectionRepository connectionRepository,
                             JwtEncoder jwtEncoder, JwtDecoder jwtDecoder) {
        this.properties = properties;
        this.stravaClient = stravaClient;
        this.connectionRepository = connectionRepository;
        this.jwtEncoder = jwtEncoder;
        this.jwtDecoder = jwtDecoder;
    }

    public String authorizeUrl(UUID userId) {
        requireConfigured();
        String state = issueState(userId);
        return properties.authBase() + "/oauth/authorize"
                + "?client_id=" + properties.clientId()
                + "&redirect_uri=" + URLEncoder.encode(properties.redirectUri(), StandardCharsets.UTF_8)
                + "&response_type=code"
                + "&approval_prompt=auto"
                + "&scope=activity:read_all"
                + "&state=" + state;
    }

    /** @return the frontend URL to redirect the browser to */
    @Transactional
    public String handleCallback(String code, String state, String error) {
        if (error != null || code == null) {
            return properties.frontendRedirect() + "?strava=error";
        }
        UUID userId;
        try {
            userId = verifyState(state);
        } catch (JwtException | IllegalArgumentException e) {
            return properties.frontendRedirect() + "?strava=error";
        }

        StravaDtos.TokenResponse token = stravaClient.exchangeCode(code);
        Instant expiresAt = Instant.ofEpochSecond(token.expiresAtEpoch());

        connectionRepository.findByUserId(userId).ifPresentOrElse(
                existing -> existing.updateTokens(token.accessToken(), token.refreshToken(), expiresAt),
                () -> connectionRepository.save(new StravaConnection(userId, token.athlete().id(),
                        token.accessToken(), token.refreshToken(), expiresAt, "activity:read_all")));

        return properties.frontendRedirect() + "?strava=connected";
    }

    @Transactional
    public void disconnect(UUID userId) {
        connectionRepository.findByUserId(userId).ifPresent(connectionRepository::delete);
    }

    /** Returns a fresh access token, refreshing (and persisting) if needed. */
    @Transactional
    public StravaConnection freshConnection(UUID userId) {
        StravaConnection connection = connectionRepository.findByUserId(userId)
                .orElseThrow(() -> new StravaException("Strava is not connected"));
        if (connection.tokenExpiringSoon()) {
            StravaDtos.TokenResponse refreshed = stravaClient.refreshToken(connection.getRefreshToken());
            connection.updateTokens(refreshed.accessToken(), refreshed.refreshToken(),
                    Instant.ofEpochSecond(refreshed.expiresAtEpoch()));
        }
        return connection;
    }

    private void requireConfigured() {
        if (!properties.configured()) {
            throw new StravaException("Strava integration is not configured "
                    + "(set CAVALE_STRAVA_CLIENT_ID and CAVALE_STRAVA_CLIENT_SECRET)");
        }
    }

    private String issueState(UUID userId) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(userId.toString())
                .claim("purpose", STATE_PURPOSE)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(600))
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    private UUID verifyState(String state) {
        Jwt jwt = jwtDecoder.decode(state);
        if (!STATE_PURPOSE.equals(jwt.getClaimAsString("purpose"))) {
            throw new IllegalArgumentException("wrong token purpose");
        }
        return UUID.fromString(jwt.getSubject());
    }
}
