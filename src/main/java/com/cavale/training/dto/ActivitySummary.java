package com.cavale.training.dto;

import java.math.BigDecimal;

import com.cavale.training.domain.Activity;
import com.cavale.training.domain.ActivitySource;

public record ActivitySummary(
        ActivitySource source,
        int durationMin,
        BigDecimal distanceKm,
        Integer elevationM,
        Integer avgHr,
        String comment) {

    public static ActivitySummary from(Activity activity) {
        return new ActivitySummary(activity.getSource(), activity.getDurationMin(), activity.getDistanceKm(),
                activity.getElevationM(), activity.getAvgHr(), activity.getComment());
    }
}
