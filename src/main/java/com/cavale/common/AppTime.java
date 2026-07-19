package com.cavale.common;

import java.time.ZoneId;

/**
 * The zone the app buckets activity dates in. Cavale is a single-athlete
 * (French) deployment, so runs and gym workouts must fall on the same calendar
 * day regardless of the host's clock — using the JVM default would file a late
 * workout on the wrong day (and ISO week) on a UTC server. Override with the
 * {@code CAVALE_TIMEZONE} env var if the athlete lives elsewhere.
 */
public final class AppTime {

    public static final ZoneId ZONE = resolveZone();

    private AppTime() {
    }

    private static ZoneId resolveZone() {
        String configured = System.getenv("CAVALE_TIMEZONE");
        return ZoneId.of(configured == null || configured.isBlank() ? "Europe/Paris" : configured.trim());
    }
}
