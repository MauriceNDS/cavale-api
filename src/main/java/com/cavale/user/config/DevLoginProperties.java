package com.cavale.user.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Passwordless login-by-email for environments without a Strava application
 * (the dev box). OFF by default and meant to stay off everywhere the app is
 * reachable by anyone but the owner — the dev deployment sits behind
 * Cloudflare Access, which is what makes this door acceptable there.
 */
@ConfigurationProperties(prefix = "cavale.dev-login")
public record DevLoginProperties(boolean enabled) {
}
