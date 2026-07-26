package com.cavale.athlete.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The deep running-statistics read model: training load (Banister),
 * weekly effort with its target band, ACWR, trail volume, aerobic
 * efficiency, duration checkpoints, race predictions, Foster
 * monotony/strain and the fused training-status verdict.
 */
public record RunningStatsResponse(
        List<DayForm> form,
        List<WeekEffort> weeklyEffort,
        Acwr acwr,
        List<WeekVolume> weeklyVolume,
        List<MonthEfficiency> efficiency,
        List<DurationCheckpoint> checkpoints,
        List<RoadPrediction> roadPredictions,
        List<TrailEstimate> trailEstimates,
        List<WeekMonotony> monotony,
        TrainingStatus trainingStatus,
        List<Vo2maxPoint> vo2maxTrend,
        CriticalPace criticalPace,
        List<DurabilityPoint> durability,
        List<WeekZones> weeklyZones,
        LongRunGuard longRunGuard) {

    /** One day of the impulse-response model (fitness 42 d, fatigue 7 d). */
    public record DayForm(LocalDate date, double fitness, double fatigue, double formScore) {
    }

    /**
     * One ISO week of time in HR zones, seconds for Z1…Z5 (Karvonen %HRR,
     * %HRmax when no resting HR). Runs without streams contribute their
     * whole duration at their average HR — flagged as partly estimated.
     */
    public record WeekZones(LocalDate weekStart, List<Integer> seconds, boolean partlyEstimated) {
    }

    /**
     * RUNSAFE long-run guard: distances where the next long run's injury
     * hazard steps up, from the 30-day longest (1.3× = +52 %, 2× = +128 %).
     */
    public record LongRunGuard(BigDecimal recentLongestKm, LocalDate longestOn,
                               BigDecimal elevatedFromKm, BigDecimal highFromKm,
                               BigDecimal lastRunKm, String lastRunBand) {
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

    /**
     * One ISO week of Foster's load-distribution metrics: monotony =
     * mean ÷ SD of the seven daily loads, strain = weekly load × monotony.
     * monotony/strain are null for a week with no training (or no day-to-day
     * variance); flagged marks monotony ≥ 2.0 — the illness / overtraining
     * early-warning even Garmin skips.
     */
    public record WeekMonotony(LocalDate weekStart, Double monotony, Integer strain,
                               boolean flagged) {
    }

    public enum TrainingStatusLabel { PRODUCTIVE, MAINTAINING, OVERREACHING, RECOVERY, DETRAINING }

    /**
     * One plain-language verdict fusing the fitness trend, the ACWR and the
     * current form, with the numbers that drove it so the UI can explain it.
     */
    public record TrainingStatus(TrainingStatusLabel label, double fitnessTrendPct,
                                 double form, double acwr) {
    }

    /**
     * One month's effective VO2max estimate (ml/kg/min): the median over that
     * month's road-like runs of each run's VO2 demand scaled by the fraction
     * of heart-rate reserve it used (%HRR ≈ %VO2R). Null months have no
     * qualifying HR run.
     */
    public record Vo2maxPoint(String month, Integer vo2max, int runs) {
    }

    /**
     * The critical-speed fit from the best-effort curve (distance = CS·t + D'):
     * the highest sustainable pace (sec/km), the critical speed (m/s), the
     * anaerobic distance reserve D' (m), how many best-effort points fed the
     * regression and its R² fit quality.
     */
    public record CriticalPace(int criticalPaceSecPerKm, double criticalSpeedMps,
                               int anaerobicCapacityM, int samples, double fitQuality) {
    }

    /**
     * Aerobic decoupling on one long run: the percent drop in efficiency
     * (speed ÷ heart rate) from the first half to the second. Under ~5 % is
     * durable; more means the athlete fades late — what decides ultra outcomes.
     */
    public record DurabilityPoint(LocalDate date, double decouplingPct, BigDecimal distanceKm,
                                  int durationMin) {
    }
}
