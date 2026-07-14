package com.cavale.athlete.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.cavale.training.domain.ActivitySource;
import com.cavale.training.domain.PerceivedEffort;

/**
 * One page of the unified history: runs (Strava-linked, standalone or
 * manual) and gym workouts, merged newest first.
 */
public record ActivityFeedResponse(List<FeedItem> items, int page, boolean hasMore, long total) {

    public enum FeedType { RUN, GYM }

    public record FeedItem(
            UUID id,
            FeedType type,
            LocalDate date,
            String title,
            Integer durationMin,
            PerceivedEffort perceivedEffort,
            boolean painFlag,
            // RUN
            BigDecimal distanceKm,
            Integer elevationM,
            Integer avgHr,
            Integer paceSecPerKm,
            ActivitySource source,
            UUID sessionId,
            // GYM
            String templateName,
            BigDecimal tonnageKg,
            Integer sets) {
    }
}
