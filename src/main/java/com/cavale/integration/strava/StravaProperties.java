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
        String frontendLoginRedirect,
        String authBase,
        String apiBase,
        /** Public URL Strava pushes events to — empty means webhooks are off. */
        String webhookCallbackUrl,
        /** Shared secret echoed during Strava's subscription validation. */
        String webhookVerifyToken) {

    public boolean configured() {
        return clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank();
    }

    public boolean webhookConfigured() {
        return configured()
                && webhookCallbackUrl != null && !webhookCallbackUrl.isBlank()
                && webhookVerifyToken != null && !webhookVerifyToken.isBlank();
    }
}
