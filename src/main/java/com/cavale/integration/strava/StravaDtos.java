package com.cavale.integration.strava;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Wire shapes from the Strava API (v3). */
public final class StravaDtos {

    private StravaDtos() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("refresh_token") String refreshToken,
            @JsonProperty("expires_at") long expiresAtEpoch,
            @JsonProperty("athlete") Athlete athlete) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Athlete(long id) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ActivitySummary(
            long id,
            String name,
            @JsonProperty("sport_type") String sportType,
            @JsonProperty("start_date_local") LocalDateTime startDateLocal,
            /** metres */
            double distance,
            /** seconds */
            @JsonProperty("moving_time") int movingTime,
            /** metres */
            @JsonProperty("total_elevation_gain") double totalElevationGain,
            @JsonProperty("average_heartrate") Double averageHeartrate) {
    }
}
