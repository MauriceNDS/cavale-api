package com.cavale.athlete.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.cavale.athlete.dto.AthleteHubResponse.DistanceRecord;
import com.cavale.athlete.dto.AthleteHubResponse.Prediction;
import com.cavale.athlete.dto.AthleteHubResponse.TrailIndex;
import com.cavale.training.domain.ObjectiveIntensity;
import com.cavale.training.domain.ObjectiveKind;
import com.cavale.training.domain.ObjectiveRole;
import com.cavale.training.domain.ObjectiveType;
import com.cavale.training.domain.PerceivedEffort;
import com.cavale.training.domain.PlanStatus;
import com.cavale.training.domain.WeekType;
import com.cavale.training.dto.ObjectiveResponse;
import com.cavale.user.domain.AthleteStatus;

/**
 * Where the athlete is RIGHT NOW, in one payload — the context a coach
 * (human or the future MCP client) must read before touching a plan:
 * availability, current season position, recent load and how it felt,
 * the last race and what comes next.
 */
public record AthleteContextResponse(
        Profile profile,
        Status status,
        Season season,
        TrainingLoadSummary trainingLoad,
        List<WeekLoad> recentWeeks,
        GymLoad gym,
        List<SessionFeedback> recentFeedback,
        LastRace lastRace,
        List<UpcomingObjective> upcoming,
        LongRunGuard longRunGuard,
        AerobicProfile aerobic,
        TrailIndex trailIndex,
        List<DistanceRecord> records,
        List<Prediction> predictions) {

    /** preferredLanguage ('fr' | 'en') is the language ALL generated content
     *  (plan names, week focus, session titles and instructions) must use. */
    public record Profile(String displayName, Integer age, BigDecimal weightKg,
                          Integer heightCm, Integer maxHr, Integer restingHr,
                          String preferredLanguage) {
    }

    public record Status(AthleteStatus status, String note, LocalDate since, Long daysSince) {
    }

    /** The active (or next) season and where the athlete stands inside it. */
    public record Season(UUID planId, String name, String goal, PlanStatus planStatus,
                         LocalDate startDate, LocalDate endDate,
                         Integer currentWeekNumber, int totalWeeks,
                         WeekType currentWeekType, String currentWeekPhase,
                         ObjectiveResponse mainObjective) {
    }

    /** One ISO week of training: what was planned, done, and how heavy it was. */
    public record WeekLoad(LocalDate weekStart, int plannedSessions, int doneSessions,
                           int skippedSessions, int runs, BigDecimal distanceKm,
                           int durationMin, int elevationM, int relativeEffort, int painFlags) {
    }

    /** How a validated run felt — the subjective trail behind the numbers. */
    public record SessionFeedback(LocalDate date, String title, PerceivedEffort perceivedEffort,
                                  boolean painFlag, String comment) {
    }

    /**
     * The load dials a coach reads first: Banister fitness/fatigue/form,
     * the acute:chronic ratio (sweet spot 0.8–1.3, danger above 1.5),
     * this week's effort against its progressive target band, Foster's
     * monotony/strain (monotonyFlag = monotony ≥ 2.0, an overtraining
     * early-warning) and the single fused training-status verdict.
     */
    public record TrainingLoadSummary(double fitness, double fatigue, double formScore,
                                      double acwr, String acwrZone,
                                      int currentWeekEffort, Integer weekBandLow,
                                      Integer weekBandHigh,
                                      Double monotony, Integer strain, boolean monotonyFlag,
                                      String trainingStatus) {
    }

    /**
     * The per-run injury guardrail (RUNSAFE): the next long run measured
     * against the athlete's trailing-30-day longest. A run up to
     * {@code elevatedFromKm} (1.3× the longest) is normal; from there to
     * {@code highFromKm} (2.0×) it is elevated risk (+52% hazard, BJSM 2025);
     * beyond it, high (+128%). lastRunKm/lastRunBand classify the most recent
     * run. This guards individual long runs; ACWR remains the chronic view.
     */
    public record LongRunGuard(BigDecimal recentLongestKm, LocalDate longestOn,
                               BigDecimal elevatedFromKm, BigDecimal highFromKm,
                               BigDecimal lastRunKm, String lastRunBand) {
    }

    /**
     * The deeper aerobic signals a coach reads: the current effective VO2max
     * estimate (ml/kg/min, from road-like runs' HR↔pace), critical pace
     * (sec/km, the highest sustainable pace from the best-effort curve), and
     * the recent aerobic decoupling on long runs (percent — under ~5 % means
     * durable, higher means the athlete fades). Any field is null when the
     * data to compute it is missing.
     */
    public record AerobicProfile(Integer effectiveVo2max, Integer criticalPaceSecPerKm,
                                 Double durabilityDecoupling) {
    }

    /** The strength side of the load: weekly tonnage and fresh records. */
    public record GymLoad(List<GymWeek> weeks, List<GymPr> recentPrs) {
    }

    public record GymWeek(LocalDate weekStart, int workouts, BigDecimal tonnageKg, int painFlags) {
    }

    public record GymPr(String exerciseName, int reps, BigDecimal weightKg, LocalDate date) {
    }

    public record LastRace(String name, LocalDate date, long daysSince, BigDecimal distanceKm,
                           Integer elevationGainM, Integer resultTimeMin, Integer targetTimeMin) {
    }

    /**
     * kind decides the target representation: for ROAD read targetTimeMin as a
     * pace over distanceKm; for TRAIL read it as a finish time over the
     * km-effort (distanceKm + elevationGainM/100). intensity (BALANCE vs
     * PERFORMANCE) sets how aggressive the ramp toward it should be.
     */
    public record UpcomingObjective(String name, LocalDate date, long daysUntil, ObjectiveType type,
                                    ObjectiveRole role, ObjectiveKind kind, ObjectiveIntensity intensity,
                                    BigDecimal distanceKm, Integer elevationGainM, Integer targetTimeMin) {
    }
}
