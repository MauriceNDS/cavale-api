package com.cavale.athlete.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.cavale.training.domain.PlanStatus;
import com.cavale.training.dto.ObjectiveResponse;

/** Everything the home/athlete hub shows, in one payload. */
public record AthleteHubResponse(
        Profile profile,
        List<Season> seasons,
        List<DistanceRecord> records,
        LongestRuns longestRuns,
        List<Prediction> predictions,
        TrailIndex trailIndex,
        Totals totals,
        List<MonthlyStat> monthly,
        List<WeeklyStat> weekly,
        List<WeeklyEffort> weeklyEffort,
        SyncState sync) {

    public record Profile(
            String displayName,
            String email,
            BigDecimal weightKg,
            Integer heightCm,
            LocalDate birthDate,
            Integer maxHr,
            Integer restingHr,
            Instant memberSince) {
    }

    public enum Timeframe { PAST, CURRENT, FUTURE }

    /** A plan and the main objective it was built around. */
    public record Season(
            UUID planId,
            String planName,
            PlanStatus planStatus,
            LocalDate startDate,
            LocalDate endDate,
            Timeframe timeframe,
            ObjectiveResponse objective) {
    }

    /** Fastest time ever over a canonical distance. */
    public record DistanceRecord(
            String label,
            int distanceM,
            int seconds,
            LocalDate date,
            String activityName) {
    }

    public record RunRef(LocalDate date, String name, BigDecimal distanceKm, int durationMin) {
    }

    public record LongestRuns(RunRef byDistance, RunRef byDuration) {
    }

    /** Riegel estimation from the closest record — not a measured time. */
    public record Prediction(
            String label,
            int distanceM,
            int seconds,
            int paceSecPerKm,
            String basedOn) {
    }

    /**
     * The personal trail performance index (ITRA/UTMB-style): a single
     * climbing number from the athlete's best trail efforts over the last
     * 36 months on the km-effort scale, recent efforts weighted higher.
     */
    public record TrailIndex(
            int index,
            int sampleEfforts,
            String bestEffortName,
            LocalDate bestEffortDate,
            BigDecimal bestKmEffort) {
    }

    public record PeriodTotals(int runs, BigDecimal distanceKm, int durationMin, int elevationM) {
    }

    public record Totals(PeriodTotals year, PeriodTotals allTime) {
    }

    public record MonthlyStat(
            String month,
            int runs,
            BigDecimal distanceKm,
            int durationMin,
            int elevationM,
            Integer avgPaceSecPerKm,
            Integer avgHr,
            BigDecimal avgCadenceSpm,
            int relativeEffort) {
    }

    /** Same trend metrics as {@link MonthlyStat}, on ISO-week buckets. */
    public record WeeklyStat(
            LocalDate weekStart,
            int runs,
            BigDecimal distanceKm,
            int durationMin,
            int elevationM,
            Integer avgPaceSecPerKm,
            Integer avgHr,
            BigDecimal avgCadenceSpm,
            int relativeEffort) {
    }

    public record WeeklyEffort(LocalDate weekStart, int relativeEffort, BigDecimal distanceKm) {
    }

    public record SyncState(boolean stravaConnected, long syncedActivities, long recordsPending) {
    }
}
