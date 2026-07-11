package com.cavale.training.dto;

import java.math.BigDecimal;

import com.cavale.training.domain.Activity;
import com.cavale.training.domain.ActivitySource;
import com.cavale.training.domain.PerceivedEffort;

public record ActivitySummary(
        ActivitySource source,
        String name,
        int durationMin,
        BigDecimal distanceKm,
        Integer elevationM,
        Integer avgHr,
        PerceivedEffort perceivedEffort,
        String comment,
        boolean hasStreams) {

    public static ActivitySummary from(Activity activity) {
        return new ActivitySummary(activity.getSource(), activity.getName(), activity.getDurationMin(),
                activity.getDistanceKm(), activity.getElevationM(), activity.getAvgHr(),
                activity.getPerceivedEffort(), activity.getComment(),
                activity.getStreamsJson() != null);
    }
}
