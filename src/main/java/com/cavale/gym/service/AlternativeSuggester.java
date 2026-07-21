package com.cavale.gym.service;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.cavale.gym.domain.Exercise;
import com.cavale.gym.domain.Muscle;

/**
 * Ranks replacement candidates for an exercise: same category (a back squat
 * never suggests a plank), at least one shared muscle, scored by muscle
 * overlap with a strong bonus when the PRIMARY muscle matches (the first of
 * the exercise's ordered muscle set) and a small one for the same measure.
 * Purely a ranking — the athlete stays free to pick anything.
 */
final class AlternativeSuggester {

    private static final int PRIMARY_MATCH_BONUS = 3;
    private static final int SHARED_MUSCLE_WEIGHT = 2;
    private static final int SAME_MEASURE_BONUS = 1;

    private AlternativeSuggester() {
    }

    static List<Exercise> suggest(Exercise base, Collection<Exercise> pool,
                                  Set<UUID> excluded, int limit) {
        Muscle primary = base.getMuscles().stream().findFirst().orElse(null);
        return pool.stream()
                .filter(e -> !e.isArchived()
                        && !e.getId().equals(base.getId())
                        && !excluded.contains(e.getId())
                        && e.getCategory() == base.getCategory()
                        && sharedMuscles(base, e) > 0)
                .sorted(Comparator.comparingInt((Exercise e) -> -score(base, e, primary))
                        .thenComparing(Exercise::getName))
                .limit(limit)
                .toList();
    }

    private static int score(Exercise base, Exercise candidate, Muscle primary) {
        int score = SHARED_MUSCLE_WEIGHT * sharedMuscles(base, candidate);
        if (primary != null && candidate.getMuscles().stream().findFirst()
                .filter(primary::equals).isPresent()) {
            score += PRIMARY_MATCH_BONUS;
        }
        if (candidate.getMeasure() == base.getMeasure()) {
            score += SAME_MEASURE_BONUS;
        }
        return score;
    }

    private static int sharedMuscles(Exercise a, Exercise b) {
        Set<Muscle> shared = new HashSet<>(a.getMuscles());
        shared.retainAll(b.getMuscles());
        return shared.size();
    }
}
