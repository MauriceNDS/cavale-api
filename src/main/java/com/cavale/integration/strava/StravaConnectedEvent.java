package com.cavale.integration.strava;

import java.util.UUID;

/** Published after a Strava connection is created or refreshed via OAuth. */
public record StravaConnectedEvent(UUID userId) {
}
