package com.cavale.common.security;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtConfigTest {

    private final OAuth2TokenValidator<Jwt> validator = JwtConfig.bearerTokenValidator();

    private static Jwt.Builder token() {
        return Jwt.withTokenValue("t")
                .header("alg", "HS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(600))
                .subject("019f78cc-40c8-7515-8fc9-ecbb77467fba");
    }

    @Test
    void acceptsSessionToken() {
        Jwt jwt = token().issuer("cavale-api").claim("purpose", "session").build();
        assertThat(validator.validate(jwt).hasErrors()).isFalse();
    }

    @Test
    void acceptsPersonalAccessToken() {
        Jwt jwt = token().issuer("cavale-api").claim("purpose", "pat").build();
        assertThat(validator.validate(jwt).hasErrors()).isFalse();
    }

    @Test
    void rejectsStravaStateToken() {
        // Same signing secret, but no issuer and a connect purpose — must not
        // be usable as a session bearer.
        Jwt jwt = token().claims(c -> c.put("purpose", "strava-connect")).build();
        assertThat(validator.validate(jwt).hasErrors()).isTrue();
    }

    @Test
    void rejectsRightIssuerButWrongPurpose() {
        Jwt jwt = token().issuer("cavale-api").claim("purpose", "strava-connect").build();
        assertThat(validator.validate(jwt).hasErrors()).isTrue();
    }

    @Test
    void rejectsMissingPurpose() {
        Jwt jwt = token().issuer("cavale-api").claims(c -> c.remove("purpose")).build();
        assertThat(validator.validate(jwt).hasErrors()).isTrue();
    }

    @Test
    void rejectsForeignIssuer() {
        Jwt jwt = token().issuer("evil").claim("purpose", "session").build();
        assertThat(validator.validate(jwt).hasErrors()).isTrue();
    }

    /**
     * The Strava "state" token (no issuer, purpose=strava-connect) must still
     * decode via the dedicated lenient decoder — otherwise the OAuth callback
     * can't validate it — while the strict bearer decoder rejects it.
     */
    @Test
    void stateTokenDecodesLenientlyButNotAsBearer() {
        JwtProperties props = new JwtProperties("state-decoder-test-secret-0123456789abcdef",
                java.time.Duration.ofHours(24), java.time.Duration.ofDays(180),
                java.time.Duration.ofDays(60), true);
        JwtConfig config = new JwtConfig(props);

        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject("019f78cc-40c8-7515-8fc9-ecbb77467fba")
                .claim("purpose", "strava-connect")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(600))
                .build();
        String state = config.jwtEncoder()
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();

        // Lenient decoder accepts it (signature + expiry only)…
        assertThat(config.stateTokenDecoder().decode(state).getClaimAsString("purpose"))
                .isEqualTo("strava-connect");
        // …but the strict resource-server decoder rejects it.
        assertThatThrownBy(() -> config.jwtDecoder().decode(state))
                .isInstanceOf(org.springframework.security.oauth2.jwt.JwtValidationException.class);
    }
}
