package com.cavale.integration.intervals;

import java.util.List;

/** Thin boundary over the Intervals.icu REST API — mockable in tests. */
public interface IntervalsClient {

    /**
     * The athlete owning the API key (athlete id {@code 0} = "whoever
     * authenticates"). Doubles as key validation: a bad key raises 401/403.
     */
    IntervalsDtos.Athlete getAthlete(String apiKey);

    /** Bulk create-or-update of planned events, matched on external_id. */
    List<IntervalsDtos.EventResult> upsertEvents(String apiKey, List<IntervalsDtos.EventPayload> events);
}
