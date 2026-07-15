package com.cavale.training.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.cavale.training.domain.WaypointKind;

/**
 * A course with its elevation profile and personalised pacing. Splits are
 * grade-adjusted segment times; waypoints carry cumulative aid-station
 * arrivals as a low/mid/high range. All time fields are null when the athlete
 * has too little trail history to derive a pace — the profile still renders.
 */
public record CourseResponse(
        UUID id,
        UUID objectiveId,
        String name,
        BigDecimal distanceKm,
        int elevationGainM,
        int elevationLossM,
        BigDecimal kmEffort,
        /** Downsampled profile: [[distance_m, elevation_m], …] for the chart. */
        List<int[]> profile,
        List<Split> splits,
        List<Waypoint> waypoints,
        Integer paceMedianSecPerKmEffort,
        Integer paceSampleRuns,
        Integer finishLowSec,
        Integer finishMidSec,
        Integer finishHighSec) {

    /** One graded segment: its climb, km-effort, own time and the cumulative arrival. */
    public record Split(BigDecimal fromKm, BigDecimal toKm, int climbM, BigDecimal kmEffort,
                        Integer segmentSec, Integer cumulativeSec) {
    }

    /** An aid station / point of interest with its cumulative arrival range. */
    public record Waypoint(UUID id, String name, WaypointKind kind, BigDecimal distanceKm,
                           Integer elevationM, String note, int climbToM, BigDecimal kmEffort,
                           Integer lowSec, Integer midSec, Integer highSec) {
    }
}
