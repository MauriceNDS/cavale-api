package com.cavale.training.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.cavale.training.domain.WeekType;

/**
 * Everything the objective page shows: the season, its objectives, and
 * target-vs-actual aggregates per week and to date.
 */
public record PlanProgressResponse(
        PlanResponse plan,
        ObjectiveResponse mainObjective,
        List<ObjectiveResponse> secondaryObjectives,
        int totalWeeks,
        Integer currentWeekNumber,
        Integer daysToObjective,
        ProgressTotals totals,
        List<WeekProgress> weeks) {

    /**
     * Season-level counters. "Past" pairs feed the adherence ratio: of the
     * sessions whose date has passed, how many were actually done.
     * "ToDate" targets cover elapsed weeks in full plus the current week
     * prorated by day, so actuals compare against where the plan says you
     * should be today — not against the whole season.
     */
    public record ProgressTotals(
            int sessionsPlanned,
            int sessionsDone,
            int sessionsSkipped,
            int sessionsDuePast,
            int sessionsDonePast,
            BigDecimal actualVolumeKm,
            int actualElevationM,
            int actualDurationMin,
            BigDecimal plannedVolumeKmToDate,
            int plannedElevationMToDate,
            BigDecimal targetVolumeKm,
            int targetElevationM) {
    }

    public record WeekProgress(
            UUID weekId,
            int weekNumber,
            LocalDate startDate,
            WeekType weekType,
            String phase,
            boolean current,
            BigDecimal targetVolumeKm,
            /** Km the prescribed session times should actually produce (personal pace model). */
            BigDecimal estimatedVolumeKm,
            Integer targetElevationM,
            Integer targetLoadUa,
            BigDecimal actualVolumeKm,
            int actualElevationM,
            int actualDurationMin,
            int sessionsPlanned,
            int sessionsDone,
            int sessionsSkipped) {
    }
}
