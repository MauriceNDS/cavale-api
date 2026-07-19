package com.cavale.common.security;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;

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
}
