package com.cavale.gym.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.cavale.gym.domain.ExerciseCategory;
import com.cavale.gym.domain.Muscle;

/** The gym progression read model — everything the stats page draws. */
public record GymStatsResponse(
        List<ExerciseTrend> oneRmTrends,
        List<WeekTonnage> weeklyTonnage,
        List<MuscleVolume> muscleVolume,
        List<PrEntry> prWall,
        List<WeekAdherence> adherence) {

    /** Estimated 1RM per training day for one tracked lift (Epley). */
    public record ExerciseTrend(UUID exerciseId, String name, ExerciseCategory category,
                                List<TrendPoint> points) {
    }

    public record TrendPoint(LocalDate date, BigDecimal bestWeightKg, BigDecimal estOneRmKg) {
    }

    public record WeekTonnage(LocalDate weekStart, BigDecimal tonnageKg, int sets, int workouts) {
    }

    /** Where the training volume lands — flags imbalances a runner should fix. */
    public record MuscleVolume(Muscle muscle, int sets, BigDecimal tonnageKg) {
    }

    /** "Squat 6 reps : 92,5 kg (+2,5) le 10 juillet" — records set recently. */
    public record PrEntry(UUID exerciseId, String exerciseName, int reps, BigDecimal weightKg,
                          BigDecimal previousKg, LocalDate date) {
    }

    public record WeekAdherence(LocalDate weekStart, int plannedGym, int doneGym) {
    }
}
