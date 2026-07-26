package com.cavale.gym.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import com.cavale.gym.domain.Exercise;
import com.cavale.gym.domain.ExerciseMeasure;

/**
 * What load to put in front of the athlete, and — just as important — why.
 *
 * <p>The rules are tried most-specific first and the winner is reported
 * alongside the number, so the runner can caption it ("75 % de 110 kg")
 * instead of producing a weight out of thin air. Everything is rounded
 * DOWN to a step the bar can actually be loaded to: proposing 83.7 kg
 * would be worse than useless.
 */
public final class WeightSuggester {

    /** How far back a lift's history still says something about today's max. */
    static final int ONE_RM_WINDOW_DAYS = 120;

    private WeightSuggester() {
    }

    public enum Source {
        /** The program prescribes a % and history gives a max to apply it to. */
        INTENSITY_OF_ONE_RM,
        /** Same load as last time, plus a step: every target rep was cleared. */
        PROGRESSED_FROM_LAST,
        /** What was lifted last time — the session was not completed as written. */
        SAME_AS_LAST,
        /** No history at all: the starting load set on the exercise. */
        REFERENCE,
        /** Nothing to go on — the field stays empty. */
        NONE
    }

    /**
     * @param weightKg the load to propose, null when nothing can be inferred
     * @param basisKg  what the rule worked from — the estimated 1RM, or the
     *                 load lifted last time — so the caption can show its work
     */
    public record Suggestion(BigDecimal weightKg, Source source, BigDecimal basisKg) {

        static final Suggestion NOTHING = new Suggestion(null, Source.NONE, null);
    }

    /** Just enough of a past set to reason about — no entity needed. */
    public record PastSet(BigDecimal weightKg, Integer reps, boolean warmup) {
    }

    /**
     * @param lastSets    the sets of the last finished workout on this exercise
     * @param oneRepMaxKg best recent estimate, null when there is no history
     */
    public static Suggestion suggest(Exercise exercise, Integer intensityPct, Integer targetReps,
                                     List<PastSet> lastSets, BigDecimal oneRepMaxKg) {
        if (exercise.getMeasure() == ExerciseMeasure.SECONDS) {
            return Suggestion.NOTHING; // a hold is not a load
        }
        BigDecimal step = exercise.effectiveIncrementKg();

        if (intensityPct != null && oneRepMaxKg != null) {
            BigDecimal target = oneRepMaxKg
                    .multiply(BigDecimal.valueOf(intensityPct))
                    .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
            return new Suggestion(floorTo(target, step), Source.INTENSITY_OF_ONE_RM, oneRepMaxKg);
        }

        BigDecimal lastWorking = lastWorkingWeight(lastSets);
        if (lastWorking != null) {
            return cleared(lastSets, targetReps)
                    ? new Suggestion(lastWorking.add(step), Source.PROGRESSED_FROM_LAST, lastWorking)
                    : new Suggestion(lastWorking, Source.SAME_AS_LAST, lastWorking);
        }

        if (exercise.getReferenceWeightKg() != null) {
            return new Suggestion(floorTo(exercise.getReferenceWeightKg(), step),
                    Source.REFERENCE, null);
        }
        return Suggestion.NOTHING;
    }

    /** The heaviest load actually worked with last time — warm-ups don't count. */
    private static BigDecimal lastWorkingWeight(List<PastSet> lastSets) {
        return lastSets.stream()
                .filter(s -> !s.warmup() && s.weightKg() != null && s.weightKg().signum() > 0)
                .map(PastSet::weightKg)
                .max(BigDecimal::compareTo)
                .orElse(null);
    }

    /**
     * Was the prescription honoured last time? Only then does the load go up.
     * A session with no rep target, or one where a set fell short, holds.
     */
    private static boolean cleared(List<PastSet> lastSets, Integer targetReps) {
        if (targetReps == null) {
            return false;
        }
        List<PastSet> working = lastSets.stream()
                .filter(s -> !s.warmup() && s.reps() != null)
                .toList();
        return !working.isEmpty() && working.stream().allMatch(s -> s.reps() >= targetReps);
    }

    /** Down, never up: a proposal you cannot load is a proposal you cannot use. */
    static BigDecimal floorTo(BigDecimal weightKg, BigDecimal step) {
        if (step == null || step.signum() <= 0) {
            return weightKg.setScale(1, RoundingMode.DOWN);
        }
        return weightKg.divide(step, 0, RoundingMode.FLOOR).multiply(step)
                .setScale(1, RoundingMode.HALF_UP);
    }
}
