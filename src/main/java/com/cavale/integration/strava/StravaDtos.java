package com.cavale.integration.strava;

import java.time.LocalDateTime;
import java.util.List;

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
    public record Athlete(long id, String firstname, String lastname) {

        public String displayName() {
            String name = ((firstname != null ? firstname : "") + " "
                    + (lastname != null ? lastname : "")).trim();
            return name.isEmpty() ? "Athlète Strava" : name;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Stream(java.util.List<Double> data) {
    }

    /** GPS stream: each sample is a [lat, lng] pair. */
    public record LatLngStream(java.util.List<java.util.List<Double>> data) {
    }

    /** Strava's only boolean stream: false while the athlete was stopped. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BooleanStream(java.util.List<Boolean> data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StreamSet(
            Stream time,
            Stream distance,
            Stream heartrate,
            Stream altitude,
            @JsonProperty("velocity_smooth") Stream velocitySmooth,
            /** Run cadence, per leg (half the usual SPM figure). */
            Stream cadence,
            /** Per-sample "was moving" flag — what separates moving from elapsed time. */
            BooleanStream moving,
            LatLngStream latlng) {
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
            @JsonProperty("average_heartrate") Double averageHeartrate,
            @JsonProperty("max_heartrate") Double maxHeartrate,
            /** strides per minute, ONE leg — double it for SPM */
            @JsonProperty("average_cadence") Double averageCadence,
            /** Strava's relative effort */
            @JsonProperty("suffer_score") Double sufferScore) {
    }

    /** A fastest split inside one activity (1k, 5k, Half-Marathon…). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BestEffort(
            String name,
            /** metres */
            double distance,
            @JsonProperty("elapsed_time") int elapsedTime) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ActivityDetail(
            long id,
            String name,
            @JsonProperty("sport_type") String sportType,
            @JsonProperty("start_date_local") LocalDateTime startDateLocal,
            /** metres */
            Double distance,
            /** seconds */
            @JsonProperty("moving_time") Integer movingTime,
            /** metres */
            @JsonProperty("total_elevation_gain") Double totalElevationGain,
            @JsonProperty("average_heartrate") Double averageHeartrate,
            @JsonProperty("average_cadence") Double averageCadence,
            @JsonProperty("max_heartrate") Double maxHeartrate,
            @JsonProperty("suffer_score") Double sufferScore,
            @JsonProperty("best_efforts") List<BestEffort> bestEfforts) {
    }

    /** A webhook push event — https://developers.strava.com/docs/webhooks/ */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WebhookEvent(
            @JsonProperty("object_type") String objectType,
            @JsonProperty("object_id") long objectId,
            @JsonProperty("aspect_type") String aspectType,
            @JsonProperty("owner_id") long ownerId,
            @JsonProperty("subscription_id") long subscriptionId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PushSubscription(long id, @JsonProperty("callback_url") String callbackUrl) {
    }
}
