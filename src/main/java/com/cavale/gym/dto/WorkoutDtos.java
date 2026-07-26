package com.cavale.gym.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.cavale.gym.domain.SetLog;
import com.cavale.gym.domain.WorkoutLog;
import com.cavale.gym.domain.WorkoutStatus;
import com.cavale.gym.service.WeightSuggester;
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
            @Min(1) Integer seconds,
            /** An approach set — kept out of every statistic. Absent means a working set. */
            Boolean warmup,
            /** Reps left in reserve, 0–4 — usually answered later, during the rest. */
            @Min(0) @Max(4) Integer rir) {
    }

    /** Rate a set after the fact, from the rest countdown that follows it. */
    public record RateSetRequest(@Min(0) @Max(4) Integer rir) {
    }

    /** Replace a block's exercise for THIS workout — the prescribed one reverts. */
    public record SwapBlockRequest(@NotNull UUID exerciseId) {
    }

    /** Adjust a block's set count for THIS workout — 0 empties it without skipping. */
    public record AdjustSetsRequest(@Min(0) @Max(10) int sets) {
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
                                 int setNumber, Integer reps, BigDecimal weightKg, Integer seconds,
                                 boolean warmup, Integer rir) {

        public static SetLogResponse from(SetLog set) {
            return new SetLogResponse(set.getId(), set.getExercise().getId(), set.getExerciseName(),
                    set.getPosition(), set.getSetNumber(), set.getReps(), set.getWeightKg(),
                    set.getSeconds(), set.isWarmup(), set.getRir());
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
            /** Ranked same-category / same-muscles candidates, beyond the declared alternatives. */
            List<ExerciseResponse> suggestedAlternatives,
            /** EFFECTIVE set count (override applied; loop count in a circuit). */
            int sets,
            /** What the template prescribes — differs from {@code sets} when adjusted live. */
            int prescribedSets,
            Integer targetReps,
            Integer targetSeconds,
            Integer restSec,
            Integer intensityPct,
            String note,
            /** Superset this block belongs to — shared with its neighbours, null when standalone. */
            String groupKey,
            /** The load to propose, already rounded to a step the kit can make. */
            BigDecimal suggestedWeightKg,
            /** Which rule produced it, so the runner can show its work. */
            WeightSuggester.Source suggestionSource,
            /** What that rule worked from: the estimated 1RM, or last time's load. */
            BigDecimal suggestionBasisKg,
            List<SetLogResponse> lastSets,
            BigDecimal recordWeightKg) {
    }

    /** Pair or unpair blocks for THIS workout — the whole prescribed list at once. */
    public record WorkoutGroupsRequest(@NotNull List<WorkoutGroupAssignment> assignments) {
    }

    public record WorkoutGroupAssignment(@NotNull UUID templateExerciseId,
                                         @Size(max = 4) String groupKey) {
    }

    /**
     * Circuit fields are DERIVED from the groups, for the benefit of the
     * current runner: they are set only when one group holds every block, in
     * which case loops is the longest member's set count and the rest is the
     * one taken after the last block. The rewritten runner reads the groups
     * directly and these go away with it.
     */
    public record WorkoutDetailResponse(WorkoutLogResponse log, List<WorkoutBlockResponse> blocks,
                                        Integer circuitLoops, Integer circuitRestSec) {
    }
}
