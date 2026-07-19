package com.cavale.integration.strava;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
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

import com.cavale.user.domain.User;
import com.cavale.user.repository.UserRepository;
import com.cavale.user.service.TokenService;
import com.cavale.user.service.UserService;

/**
 * Strava OAuth, two flavours sharing one callback:
 *
 * CONNECT — an authenticated user links Strava from settings.
 * LOGIN   — "Continuer avec Strava" on the login page: the athlete IS the
 *           identity. First login creates the Cavale account and the
 *           connection in one stroke (how Strava-ecosystem apps onboard).
 *
 * The authorize URL carries a short-lived signed state so the callback
 * (a browser redirect from strava.com, no bearer token) stays user-bound
 * and CSRF-safe.
 */
@Service
public class StravaAuthService {

    private static final String PURPOSE_CONNECT = "strava-connect";
    private static final String PURPOSE_LOGIN = "strava-login";

    private final StravaProperties properties;
    private final StravaClient stravaClient;
    private final StravaConnectionRepository connectionRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;
    private final ApplicationEventPublisher eventPublisher;

    public StravaAuthService(StravaProperties properties, StravaClient stravaClient,
                             StravaConnectionRepository connectionRepository,
                             UserRepository userRepository, UserService userService,
                             PasswordEncoder passwordEncoder,
                             TokenService tokenService, JwtEncoder jwtEncoder, JwtDecoder jwtDecoder,
                             ApplicationEventPublisher eventPublisher) {
        this.properties = properties;
        this.stravaClient = stravaClient;
        this.connectionRepository = connectionRepository;
        this.userRepository = userRepository;
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.jwtEncoder = jwtEncoder;
        this.jwtDecoder = jwtDecoder;
        this.eventPublisher = eventPublisher;
    }

    public String authorizeUrl(UUID userId) {
        return buildAuthorizeUrl(issueState(PURPOSE_CONNECT, userId.toString()));
    }

    public String loginUrl() {
        return buildAuthorizeUrl(issueState(PURPOSE_LOGIN, "anonymous"));
    }

    /** @return the frontend URL to redirect the browser to */
    @Transactional
    public String handleCallback(String code, String state, String error) {
        String purpose;
        String subject;
        try {
            Jwt jwt = jwtDecoder.decode(state);
            purpose = jwt.getClaimAsString("purpose");
            subject = jwt.getSubject();
            if (!PURPOSE_CONNECT.equals(purpose) && !PURPOSE_LOGIN.equals(purpose)) {
                throw new IllegalArgumentException("wrong token purpose");
            }
        } catch (JwtException | IllegalArgumentException | NullPointerException e) {
            return properties.frontendRedirect() + "?strava=error";
        }

        boolean isLogin = PURPOSE_LOGIN.equals(purpose);
        if (error != null || code == null) {
            return isLogin
                    ? properties.frontendLoginRedirect() + "#error"
                    : properties.frontendRedirect() + "?strava=error";
        }

        StravaDtos.TokenResponse token = stravaClient.exchangeCode(code);
        return isLogin ? completeLogin(token) : completeConnect(UUID.fromString(subject), token);
    }

    private String completeConnect(UUID userId, StravaDtos.TokenResponse token) {
        upsertConnection(userId, token);
        return properties.frontendRedirect() + "?strava=connected";
    }

    private String completeLogin(StravaDtos.TokenResponse token) {
        long athleteId = token.athlete().id();
        User user = connectionRepository.findByAthleteId(athleteId)
                .map(connection -> userRepository.findById(connection.getUserId()).orElseThrow())
                .orElseGet(() -> createUserForAthlete(token.athlete()));

        upsertConnection(user.getId(), token);
        String cavaleJwt = tokenService.issueFor(user);
        // fragment (#), not query: it stays out of server logs and referrers
        return properties.frontendLoginRedirect() + "#token=" + cavaleJwt;
    }

    /**
     * Strava's API exposes no email, so Strava-born accounts get a synthetic
     * unique address; the random password is unusable — Strava IS the login.
     */
    private User createUserForAthlete(StravaDtos.Athlete athlete) {
        String email = "strava-" + athlete.id() + "@users.cavale.local";
        String unusablePassword = passwordEncoder.encode(UUID.randomUUID().toString());
        User user = new User(email, unusablePassword, athlete.displayName());
        userService.bootstrapIfFirstAccount(user);
        return userRepository.save(user);
    }

    private void upsertConnection(UUID userId, StravaDtos.TokenResponse token) {
        Instant expiresAt = Instant.ofEpochSecond(token.expiresAtEpoch());
        connectionRepository.findByUserId(userId).ifPresentOrElse(
                existing -> existing.updateTokens(token.accessToken(), token.refreshToken(), expiresAt),
                () -> connectionRepository.save(new StravaConnection(userId, token.athlete().id(),
                        token.accessToken(), token.refreshToken(), expiresAt, "activity:read_all")));
        // after commit, the onboarding listener pulls the athlete's whole history
        eventPublisher.publishEvent(new StravaConnectedEvent(userId));
    }

    @Transactional
    public void disconnect(UUID userId) {
        connectionRepository.findByUserId(userId).ifPresent(connectionRepository::delete);
    }

    /**
     * Returns a fresh access token, refreshing (and persisting) if needed.
     *
     * Deliberately NOT @Transactional: every caller already runs in a
     * transaction, and a proxy of its own would mark that outer transaction
     * rollback-only whenever this throws (no connection, refresh failure) —
     * even when the caller catches the exception as a best-effort step
     * (UnexpectedRollbackException at commit). Callers must be transactional
     * for the token refresh to persist.
     */
    public StravaConnection freshConnection(UUID userId) {
        // Write-lock the row: if another thread is mid-refresh, we block until
        // it commits, then read the rotated token instead of re-spending the
        // now-consumed refresh token (Strava rotates them single-use).
        StravaConnection connection = connectionRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new StravaException("Strava is not connected"));
        if (connection.tokenExpiringSoon()) {
            StravaDtos.TokenResponse refreshed = stravaClient.refreshToken(connection.getRefreshToken());
            connection.updateTokens(refreshed.accessToken(), refreshed.refreshToken(),
                    Instant.ofEpochSecond(refreshed.expiresAtEpoch()));
        }
        return connection;
    }

    private String buildAuthorizeUrl(String state) {
        requireConfigured();
        return properties.authBase() + "/oauth/authorize"
                + "?client_id=" + properties.clientId()
                + "&redirect_uri=" + URLEncoder.encode(properties.redirectUri(), StandardCharsets.UTF_8)
                + "&response_type=code"
                + "&approval_prompt=auto"
                // request EXACTLY what Strava grants (it always adds `read`):
                // a narrower request than the grant re-triggers the consent
                // screen on every login despite approval_prompt=auto
                + "&scope=read,activity:read_all"
                + "&state=" + state;
    }

    private void requireConfigured() {
        if (!properties.configured()) {
            throw new StravaException("Strava integration is not configured "
                    + "(set CAVALE_STRAVA_CLIENT_ID and CAVALE_STRAVA_CLIENT_SECRET)");
        }
    }

    private String issueState(String purpose, String subject) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(subject)
                .claim("purpose", purpose)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(600))
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
