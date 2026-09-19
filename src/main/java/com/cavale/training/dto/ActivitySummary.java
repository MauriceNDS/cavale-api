package com.cavale.training.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.cavale.training.domain.Activity;
import com.cavale.training.domain.ActivitySource;
import com.cavale.training.domain.PerceivedEffort;

public record ActivitySummary(
        ActivitySource source,
        String name,
        int durationMin,
        /** Exact moving seconds — null when the source only gave minutes. */
        Integer durationSec,
        BigDecimal distanceKm,
        Integer elevationM,
        Integer avgHr,
        BigDecimal avgCadenceSpm,
        PerceivedEffort perceivedEffort,
        boolean painFlag,
        String comment,
        boolean hasStreams,
        UUID activityId,
        UUID shoeId) {

    public static ActivitySummary from(Activity activity) {
        return new ActivitySummary(activity.getSource(), activity.getName(), activity.getDurationMin(),
                activity.getDurationSec(), activity.getDistanceKm(), activity.getElevationM(), activity.getAvgHr(),
                activity.getAvgCadenceSpm(), activity.getPerceivedEffort(), activity.isPainFlag(),
                activity.getComment(), activity.getStreamsJson() != null,
                activity.getId(), activity.getShoeId());
    }
}
