package com.cavale.athlete.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.cavale.training.domain.Activity;
import com.cavale.training.domain.ActivitySource;
import com.cavale.training.domain.PerceivedEffort;

/**
 * A single past activity in full — what the activities page shows when you open
 * one. Covers standalone (off-plan) runs and rides as well as those attached to
 * a planned session (sessionId is then set, so the UI can link across).
 */
public record ActivityDetailResponse(
        UUID id,
        ActivitySource source,
        String name,
        LocalDate date,
        UUID sessionId,
        int durationMin,
        BigDecimal distanceKm,
        Integer elevationM,
        Integer avgHr,
        Integer maxHr,
        BigDecimal avgCadenceSpm,
        Integer relativeEffort,
        PerceivedEffort perceivedEffort,
        boolean painFlag,
        String comment,
        boolean hasStreams,
        UUID shoeId,
        com.cavale.training.domain.Discipline discipline,
        /** Encoded-polyline GPS trace for the map — null on pre-capture activities. */
        String mapPolyline,
        /** First-half vs second-half HR/pace drift %, on long-enough runs with streams. */
        Double decouplingPct) {

    public static ActivityDetailResponse from(Activity activity, Double decouplingPct) {
        return new ActivityDetailResponse(
                activity.getId(),
                activity.getSource(),
                activity.getName(),
                activity.getDate(),
                activity.getSession() != null ? activity.getSession().getId() : null,
                activity.getDurationMin(),
                activity.getDistanceKm(),
                activity.getElevationM(),
                activity.getAvgHr(),
                activity.getMaxHr(),
                activity.getAvgCadenceSpm(),
                activity.getRelativeEffort(),
                activity.getPerceivedEffort(),
                activity.isPainFlag(),
                activity.getComment(),
                activity.getStreamsJson() != null,
                activity.getShoeId(),
                activity.getDiscipline(),
                activity.getMapPolyline(),
                decouplingPct);
    }
}
