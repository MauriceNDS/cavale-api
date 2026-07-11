package com.cavale.integration.strava;

import java.time.Instant;
import java.util.List;

/** Thin boundary over the Strava HTTP API — mockable in tests. */
public interface StravaClient {

    StravaDtos.TokenResponse exchangeCode(String code);

    StravaDtos.TokenResponse refreshToken(String refreshToken);

    List<StravaDtos.ActivitySummary> listActivities(String accessToken, Instant after, Instant before);

    StravaDtos.StreamSet getStreams(String accessToken, long activityId);
}
