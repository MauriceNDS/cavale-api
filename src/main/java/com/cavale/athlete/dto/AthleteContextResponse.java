package com.cavale.athlete.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.cavale.athlete.dto.AthleteHubResponse.DistanceRecord;
import com.cavale.athlete.dto.AthleteHubResponse.Prediction;
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
        List<DistanceRecord> records,
        List<Prediction> predictions) {

    public record Profile(String displayName, Integer age, BigDecimal weightKg,
                          Integer heightCm, Integer maxHr, Integer restingHr) {
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
     * the acute:chronic ratio (sweet spot 0.8–1.3, danger above 1.5) and
     * this week's effort against its progressive target band.
     */
    public record TrainingLoadSummary(double fitness, double fatigue, double formScore,
                                      double acwr, String acwrZone,
                                      int currentWeekEffort, Integer weekBandLow,
                                      Integer weekBandHigh) {
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

    public record UpcomingObjective(String name, LocalDate date, long daysUntil, ObjectiveType type,
                                    ObjectiveRole role, BigDecimal distanceKm, Integer targetTimeMin) {
    }
}
