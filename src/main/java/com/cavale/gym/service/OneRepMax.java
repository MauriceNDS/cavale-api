package com.cavale.gym.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.cavale.gym.domain.SetLog;

/**
 * The one place that turns a performed set into an estimated one-rep max.
 *
 * <p>Epley — 1RM ≈ w × (1 + reps/30) — corrected by the reps left in
 * reserve: a set stopped three short of failure is evidence of a much
 * bigger max than the same set taken to the limit, so the reserve counts
 * as reps that could have been done. Without a rating the set is read at
 * face value, which is the conservative reading and what the estimate has
 * always assumed.
 */
public final class OneRepMax {

    private OneRepMax() {
    }

    /** Null when the set carries no external load or no reps to extrapolate from. */
    public static BigDecimal of(SetLog set) {
        if (set.isWarmup() || set.getWeightKg() == null || set.getReps() == null
                || set.getReps() <= 0) {
            return null;
        }
        return estimate(set.getWeightKg(), set.getReps(), set.getRir());
    }

    public static BigDecimal estimate(BigDecimal weightKg, int reps, Integer rir) {
        int effectiveReps = reps + (rir != null ? rir : 0);
        return weightKg
                .multiply(BigDecimal.ONE.add(BigDecimal.valueOf(effectiveReps)
                        .divide(BigDecimal.valueOf(30), 4, RoundingMode.HALF_UP)))
                .setScale(1, RoundingMode.HALF_UP);
    }
}
