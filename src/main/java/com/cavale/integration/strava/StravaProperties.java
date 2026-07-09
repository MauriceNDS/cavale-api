package com.cavale.integration.strava;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Strava API credentials come from the owner's Strava API application
 * (strava.com/settings/api). Empty client id = integration disabled.
 */
@ConfigurationProperties(prefix = "cavale.strava")
public record StravaProperties(
        String clientId,
        String clientSecret,
        String redirectUri,
        String frontendRedirect,
        String authBase,
        String apiBase) {

    public boolean configured() {
        return clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank();
    }
}
