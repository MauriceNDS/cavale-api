package com.cavale.gym.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.cavale.gym.domain.SetLog;
import com.cavale.gym.domain.WorkoutLog;
import com.cavale.gym.domain.WorkoutStatus;
import com.cavale.training.domain.PerceivedEffort;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request/response shapes of the live workout. */
public final class WorkoutDtos {

    private WorkoutDtos() {
    }

    /* ── Requests ─────────────────────────────────────────────────────── */

    /** Start from a planned session (its linked variant) OR straight from a variant. */
    public record StartWorkoutRequest(UUID sessionId, UUID templateVariantId) {
    }

    public record LogSetRequest(
            @NotNull UUID exerciseId,
            @Min(0) int position,
            @Min(value = 1, message = "Set number is 1-based") int setNumber,
            @Min(1) Integer reps,
            @Min(0) BigDecimal weightKg,
            @Min(1) Integer seconds) {
    }

    /** Replace a block's exercise for THIS workout — the prescribed one reverts. */
    public record SwapBlockRequest(@NotNull UUID exerciseId) {
    }

    /** An exercise added on top of the program for THIS workout only. */
    public record AddExtraBlockRequest(
            @NotNull UUID exerciseId,
            @Min(value = 1, message = "At least one set") int sets,
            @Min(1) Integer reps,
            @Min(1) Integer seconds,
            @Min(0) Integer restSec,
            @Size(max = 300) String note) {
    }

    public record FinishWorkoutRequest(
            @Min(1) @Max(600) Integer durationMin,
            PerceivedEffort perceivedEffort,
            Boolean painFlag,
            @Size(max = 2000) String comment) {
    }

    /* ── Responses ────────────────────────────────────────────────────── */

    public record SetLogResponse(UUID id, UUID exerciseId, String exerciseName, int position,
                                 int setNumber, Integer reps, BigDecimal weightKg, Integer seconds) {

        public static SetLogResponse from(SetLog set) {
            return new SetLogResponse(set.getId(), set.getExercise().getId(), set.getExerciseName(),
                    set.getPosition(), set.getSetNumber(), set.getReps(), set.getWeightKg(),
                    set.getSeconds());
        }
    }

    public record WorkoutLogResponse(UUID id, WorkoutStatus status, Instant startedAt,
                                     Integer durationMin, String templateName, UUID sessionId,
                                     UUID templateVariantId, PerceivedEffort perceivedEffort,
                                     boolean painFlag, String comment, List<SetLogResponse> sets) {

        public static WorkoutLogResponse from(WorkoutLog log, List<SetLogResponse> sets) {
            return new WorkoutLogResponse(log.getId(), log.getStatus(), log.getStartedAt(),
                    log.getDurationMin(), log.getTemplateName(),
                    log.getSession() != null ? log.getSession().getId() : null,
                    log.getTemplateVariant() != null ? log.getTemplateVariant().getId() : null,
                    log.getPerceivedEffort(), log.isPainFlag(), log.getComment(), sets);
        }
    }

    /**
     * One exercise block of the live screen: the prescription, what the
     * athlete did LAST time (prefill), and the record at the target reps.
     * {@code exercise} is the EFFECTIVE one (a swap shows the replacement,
     * with the prescribed exercise in {@code swappedFrom}); a skipped block
     * stays in the list, flagged, so it can be restored and honest in history.
     * Exactly one of {@code templateExerciseId} (programmed block) and
     * {@code extraBlockId} (added mid-workout) is set.
     */
    public record WorkoutBlockResponse(
            UUID templateExerciseId,
            UUID extraBlockId,
            ExerciseResponse exercise,
            ExerciseResponse swappedFrom,
            boolean skipped,
            List<ExerciseResponse> alternatives,
            int sets,
            Integer targetReps,
            Integer targetSeconds,
            Integer restSec,
            Integer intensityPct,
            String note,
            List<SetLogResponse> lastSets,
            BigDecimal recordWeightKg) {
    }

    public record WorkoutDetailResponse(WorkoutLogResponse log, List<WorkoutBlockResponse> blocks) {
    }
}
