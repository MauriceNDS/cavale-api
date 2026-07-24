package com.cavale.user.service;

import java.time.Instant;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.cavale.common.security.JwtProperties;
import com.cavale.user.domain.User;

@Service
public class TokenService {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;

    public TokenService(JwtEncoder jwtEncoder, JwtProperties jwtProperties) {
        this.jwtEncoder = jwtEncoder;
        this.jwtProperties = jwtProperties;
    }

    public String issueFor(User user) {
        return issue(user, jwtProperties.ttl(), "session");
    }

    /**
     * Long-lived personal access token — the credential an MCP client
     * (the owner's Claude) presents on every call. Same HS256 signature as
     * session tokens, longer TTL, marked with purpose=pat. The {@code jti}
     * ties the JWT to its {@code personal_token} bookkeeping row so it can be
     * revoked individually.
     */
    public IssuedToken issuePersonalToken(User user, java.util.UUID jti) {
        Instant expiresAt = Instant.now().plus(jwtProperties.patTtl());
        return new IssuedToken(issue(user, jwtProperties.patTtl(), "pat", jti), expiresAt);
    }

    public record IssuedToken(String token, Instant expiresAt) {
    }

    /** Short-lived token for an ephemeral demo session. */
    public String issueDemoToken(User user, java.time.Duration ttl) {
        return issue(user, ttl, "demo");
    }

    private String issue(User user, java.time.Duration ttl, String purpose) {
        return issue(user, ttl, purpose, null);
    }

    private String issue(User user, java.time.Duration ttl, String purpose, java.util.UUID jti) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer("cavale-api")
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .claim("email", user.getEmail())
                .claim("name", user.getDisplayName())
                .claim("purpose", purpose)
                .claim("tv", user.getTokenVersion());
        if (jti != null) {
            claims.id(jti.toString());
        }
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }
}
