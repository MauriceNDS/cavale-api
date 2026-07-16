package com.cavale.demo;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Ephemeral demo-sandbox tuning. Env-overridable via {@code CAVALE_DEMO_*}.
 *
 * @param enabled      whether the "try the demo" endpoint is live
 * @param accountTtl   how long a demo account survives before the reaper deletes it
 * @param tokenTtl     lifetime of the demo JWT (short — it's a throwaway session)
 * @param maxLive      hard cap on concurrent demo accounts (DoS / bloat guard)
 * @param reapInterval how often the reaper sweeps for expired demo accounts
 */
@ConfigurationProperties(prefix = "cavale.demo")
public record DemoProperties(
        boolean enabled,
        Duration accountTtl,
        Duration tokenTtl,
        int maxLive,
        Duration reapInterval) {

    public DemoProperties {
        if (accountTtl == null) accountTtl = Duration.ofHours(24);
        if (tokenTtl == null) tokenTtl = Duration.ofHours(6);
        if (reapInterval == null) reapInterval = Duration.ofMinutes(30);
        if (maxLive <= 0) maxLive = 40;
    }
}
