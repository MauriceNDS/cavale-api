package com.cavale.common.security;

import java.util.Set;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

/**
 * Symmetric (HS256) JWT setup. Single-service API: the issuer and the
 * verifier are the same application, so a shared secret is appropriate —
 * asymmetric keys become worthwhile once other services must verify tokens.
 *
 * <p>The same secret also signs short-lived Strava OAuth "state" tokens and
 * personal access tokens, so the resource-server decoder must not accept just
 * any well-signed token as a session: it requires our issuer AND a
 * bearer-eligible {@code purpose} claim. That keeps a leaked state token (which
 * rides in the authorize URL) from being replayed as a bearer credential.
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

    static final String ISSUER = "cavale-api";

    /** Token purposes that may authenticate an API call as their subject. */
    static final Set<String> BEARER_PURPOSES = Set.of("session", "pat", "demo");

    private final JwtProperties properties;

    public JwtConfig(JwtProperties properties) {
        this.properties = properties;
    }

    private SecretKey secretKey() {
        return new SecretKeySpec(properties.secret().getBytes(), "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey()));
    }

    @Bean
    JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey())
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(bearerTokenValidator());
        return decoder;
    }

    /**
     * Timestamp + issuer (cavale-api) + a bearer-eligible purpose. Rejects
     * anything that is merely well-signed — notably the Strava "state" token,
     * which shares the secret but carries no issuer and a different purpose.
     */
    static OAuth2TokenValidator<Jwt> bearerTokenValidator() {
        OAuth2TokenValidator<Jwt> defaults = JwtValidators.createDefaultWithIssuer(ISSUER);
        return token -> {
            OAuth2TokenValidatorResult base = defaults.validate(token);
            if (base.hasErrors()) {
                return base;
            }
            String purpose = token.getClaimAsString("purpose");
            if (purpose == null || !BEARER_PURPOSES.contains(purpose)) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                        "invalid_token", "token is not usable as a bearer credential", null));
            }
            return OAuth2TokenValidatorResult.success();
        };
    }
}
