package com.cavale.athlete.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The deep running-statistics read model: training load (Banister),
 * weekly effort with its target band, ACWR, trail volume, aerobic
 * efficiency, duration checkpoints and race predictions.
 */
public record RunningStatsResponse(
        List<DayForm> form,
        List<WeekEffort> weeklyEffort,
        Acwr acwr,
        List<WeekVolume> weeklyVolume,
        List<MonthEfficiency> efficiency,
        List<DurationCheckpoint> checkpoints,
        List<RoadPrediction> roadPredictions,
        List<TrailEstimate> trailEstimates) {

    /** One day of the impulse-response model (fitness 42 d, fatigue 7 d). */
    public record DayForm(LocalDate date, double fitness, double fatigue, double formScore) {
    }

    /**
     * One ISO week of relative effort against its target band
     * (0.8–1.3 × the trailing 3-week average — the progressive zone).
     */
    public record WeekEffort(LocalDate weekStart, int effort, Integer bandLow, Integer bandHigh,
                             boolean partlyEstimated) {
    }

    public enum AcwrZone { UNDER, OPTIMAL, CAUTION, DANGER }

    /** Acute (7 d) vs chronic (28 d) load — the injury-risk dial. */
    public record Acwr(double ratio, int acute7d, int chronicWeeklyAvg, AcwrZone zone) {
    }

    /** Weekly volume in trail currency: km, D+, time and km-effort (km + D+/100). */
    public record WeekVolume(LocalDate weekStart, BigDecimal distanceKm, int elevationM,
                             int durationMin, BigDecimal kmEffort, int runs) {
    }

    /** Metres covered per heartbeat (speed ÷ HR) — rising means fitter. */
    public record MonthEfficiency(String month, BigDecimal metersPerBeat, int runs) {
    }

    /** "After 1 h of running you are typically at X km and Y m D+." */
    public record DurationCheckpoint(int minutes, int samples, BigDecimal medianDistanceKm,
                                     Integer medianElevationM, Integer medianPaceSecPerKm) {
    }

    /** One road distance across prediction models — a range, not one number. */
    public record RoadPrediction(String label, int distanceM, String baseLabel, Integer baseSec,
                                 Integer riegelSec, Integer cameronSec, Integer vickersSec,
                                 Integer recordSec) {
    }

    /** A trail objective timed from the athlete's own pace per km-effort. */
    public record TrailEstimate(String objectiveName, LocalDate date, BigDecimal distanceKm,
                                Integer elevationM, BigDecimal kmEffort, Integer lowSec,
                                Integer midSec, Integer highSec, int sampleRuns) {
    }
}
