package com.cavale.integration.strava;

import java.time.Instant;
import java.util.List;

/** Thin boundary over the Strava HTTP API — mockable in tests. */
public interface StravaClient {

    StravaDtos.TokenResponse exchangeCode(String code);

    StravaDtos.TokenResponse refreshToken(String refreshToken);

    List<StravaDtos.ActivitySummary> listActivities(String accessToken, Instant after, Instant before);

    /** One page of the athlete's full history, newest first (Strava's default order). */
    List<StravaDtos.ActivitySummary> listActivitiesPage(String accessToken, int page, int perPage);

    StravaDtos.ActivityDetail getActivity(String accessToken, long activityId);

    StravaDtos.StreamSet getStreams(String accessToken, long activityId);
}
