package com.cavale.integration.intervals;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Intervals.icu needs no application credentials: each athlete brings their
 * own API key (intervals.icu → Settings → Developer). The integration is
 * therefore always "configured"; a user is connected once their key is saved.
 */
@ConfigurationProperties(prefix = "cavale.intervals")
public record IntervalsProperties(
        String apiBase,
        /** How many days ahead of today get pushed to the calendar. */
        int pushWindowDays) {
}
