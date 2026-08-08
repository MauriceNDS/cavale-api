package com.cavale.user.service;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cavale.common.security.JwtProperties;
import com.cavale.user.domain.RefreshToken;
import com.cavale.user.domain.User;
import com.cavale.user.repository.RefreshTokenRepository;

/**
 * Issues and rotates the long-lived credential that spares the athlete a
 * daily sign-in.
 *
 * <p>Access tokens stay short (24h) because nothing can revoke a JWT that has
 * already been handed out. The refresh token is the opposite trade: opaque,
 * stored (hashed) and therefore revocable, but never sent anywhere except the
 * refresh endpoint.
 *
 * <p>Rotation is the security property worth having. Each use mints a
 * successor and revokes the presented token, so a stolen token is good for at
 * most one refresh — and the moment the victim's client uses the token the
 * thief already spent, a revoked token comes back and the whole account's
 * chain is cut. That check is the reason revoked rows are kept rather than
 * deleted on use.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    /** 256 bits of entropy — guessing is not an attack path. */
    private static final int SECRET_BYTES = 32;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final RefreshTokenRepository repository;
    private final UserService userService;
    private final JwtProperties jwtProperties;

    public RefreshTokenService(RefreshTokenRepository repository, UserService userService,
                               JwtProperties jwtProperties) {
        this.repository = repository;
        this.userService = userService;
        this.jwtProperties = jwtProperties;
    }

    /** The opaque secret to hand the client, and when it stops working. */
    public record IssuedRefresh(String secret, Instant expiresAt) {
    }

    @Transactional
    public IssuedRefresh issueFor(UUID userId) {
        return issue(userId, null);
    }

    /**
     * Trade a refresh token for its successor.
     *
     * @throws InvalidRefreshTokenException when the token is unknown, expired,
     *         or already spent — the caller must treat all three alike and
     *         send the athlete back to the sign-in page
     */
    // noRollbackFor is load-bearing: the replay case revokes the account's
    // tokens and THEN throws, and a plain @Transactional would roll the
    // revocation back on the way out — leaving the thief's chain alive.
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public Rotated rotate(String presented) {
        Instant now = Instant.now();
        RefreshToken existing = repository.findByTokenHash(hash(presented))
                .orElseThrow(() -> new InvalidRefreshTokenException("Unknown refresh token"));

        if (!existing.isLive(now)) {
            // A token that was already spent coming back means the secret is
            // in two places. Which holder is the honest one is unknowable, so
            // cut every session and make both sign in again.
            if (existing.getRevokedAt() != null) {
                log.warn("Refresh token replay for user {} — revoking every live token",
                        existing.getUserId());
                repository.revokeAllForUser(existing.getUserId(), now);
            }
            throw new InvalidRefreshTokenException("Refresh token is no longer valid");
        }

        User user = userService.getById(existing.getUserId());
        IssuedRefresh successor = issue(user.getId(), existing);
        return new Rotated(user, successor);
    }

    public record Rotated(User user, IssuedRefresh refresh) {
    }

    /** Sign-out: this device's token stops working, others keep theirs. */
    @Transactional
    public void revoke(String presented) {
        repository.findByTokenHash(hash(presented))
                .ifPresent(token -> token.revoke(Instant.now()));
    }

    /** Every device signs out — pairs with an account-wide token revocation. */
    @Transactional
    public void revokeAllFor(UUID userId) {
        repository.revokeAllForUser(userId, Instant.now());
    }

    private IssuedRefresh issue(UUID userId, RefreshToken predecessor) {
        byte[] bytes = new byte[SECRET_BYTES];
        RANDOM.nextBytes(bytes);
        String secret = ENCODER.encodeToString(bytes);

        Instant now = Instant.now();
        Instant expiresAt = now.plus(jwtProperties.refreshTtl());
        RefreshToken token = repository.save(new RefreshToken(userId, hash(secret), now, expiresAt));
        if (predecessor != null) {
            predecessor.replaceWith(token.getId(), now);
        }
        return new IssuedRefresh(secret, expiresAt);
    }

    /**
     * SHA-256 is right here where a slow KDF would be wrong: the input is 256
     * random bits, not a guessable password, so there is no dictionary to
     * mount and nothing for work factor to buy.
     */
    static String hash(String secret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the JVM spec", e);
        }
    }
}
