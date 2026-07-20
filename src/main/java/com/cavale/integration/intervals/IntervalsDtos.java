package com.cavale.integration.intervals;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Wire shapes for the Intervals.icu API (v1). */
public final class IntervalsDtos {

    private IntervalsDtos() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Athlete(String id, String name) {
    }

    /**
     * A planned calendar event. {@code external_id} is Cavale's session id —
     * re-pushing the same id updates the event in place (upsert), so plan
     * adaptations never duplicate workouts on the calendar or the watch.
     */
    public record EventPayload(
            String category,
            @JsonProperty("start_date_local") String startDateLocal,
            String type,
            String name,
            String description,
            @JsonProperty("external_id") String externalId) {

        public static EventPayload workout(String startDateLocal, String type, String name,
                                           String description, String externalId) {
            return new EventPayload("WORKOUT", startDateLocal, type, name, description, externalId);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EventResult(long id, @JsonProperty("external_id") String externalId) {
    }
}
