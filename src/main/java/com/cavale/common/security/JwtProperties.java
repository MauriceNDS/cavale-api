package com.cavale.common.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cavale.security.jwt")
public record JwtProperties(String secret, Duration ttl, Duration patTtl, Duration refreshTtl,
                            boolean secureCookie) {

    public JwtProperties {
        if (secret == null || secret.getBytes().length < 32) {
            throw new IllegalStateException("cavale.security.jwt.secret must be at least 32 bytes for HS256");
        }
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            throw new IllegalStateException("cavale.security.jwt.ttl must be a positive duration");
        }
        if (patTtl == null || patTtl.isNegative() || patTtl.isZero()) {
            throw new IllegalStateException("cavale.security.jwt.pat-ttl must be a positive duration");
        }
        if (refreshTtl == null || refreshTtl.isNegative() || refreshTtl.isZero()) {
            throw new IllegalStateException("cavale.security.jwt.refresh-ttl must be a positive duration");
        }
    }
}
