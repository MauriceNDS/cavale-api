package com.cavale.training.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.cavale.training.domain.Activity;

/** An ingested Strava run proposed as the realization of a planned session. */
public record SessionProposalResponse(
        UUID activityId,
        long stravaActivityId,
        String name,
        LocalDate date,
        int durationMin,
        BigDecimal distanceKm,
        Integer elevationM,
        Integer avgHr) {

    public static SessionProposalResponse from(Activity activity) {
        return new SessionProposalResponse(activity.getId(), activity.getExternalId(),
                activity.getName(), activity.getDate(), activity.getDurationMin(),
                activity.getDistanceKm(), activity.getElevationM(), activity.getAvgHr());
    }
}
