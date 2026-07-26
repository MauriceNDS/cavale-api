package com.cavale.gym.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * One lift's whole story — every session it appeared in, what was actually
 * performed, and where it is going.
 *
 * <p>The aggregate stats can only ever say which lifts are moving; this is
 * where you find out why one of them stopped.
 */
public record ExerciseHistoryResponse(
        UUID exerciseId,
        String name,
        /** Newest first — the way you read back through a training log. */
        List<Session> sessions,
        /** Best estimated 1RM per session, oldest first, for the curve. */
        List<Point> oneRmTrend,
        /** Heaviest working set ever, at any rep count. */
        BigDecimal bestWeightKg,
        BigDecimal bestOneRmKg,
        /** Total working sets ever logged on this exercise. */
        int totalSets,
        /**
         * Sessions since the estimated 1RM last improved — a lift that has
         * not moved in a while is the one worth changing something about.
         */
        Integer sessionsSinceProgress) {

    public record Session(UUID workoutLogId, LocalDate date, String templateName,
                          List<PerformedSet> sets, BigDecimal topWeightKg,
                          BigDecimal estOneRmKg, BigDecimal tonnageKg) {
    }

    public record PerformedSet(int setNumber, Integer reps, BigDecimal weightKg, Integer seconds,
                               boolean warmup, Integer rir) {
    }

    public record Point(LocalDate date, BigDecimal estOneRmKg) {
    }
}
