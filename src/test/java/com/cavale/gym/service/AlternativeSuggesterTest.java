package com.cavale.gym.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.cavale.gym.domain.Equipment;
import com.cavale.gym.domain.Exercise;
import com.cavale.gym.domain.ExerciseCategory;
import com.cavale.gym.domain.ExerciseMeasure;
import com.cavale.gym.domain.Muscle;

import static org.assertj.core.api.Assertions.assertThat;

class AlternativeSuggesterTest {

    private static final UUID USER = UUID.randomUUID();

    private static Exercise exercise(String name, ExerciseCategory category,
                                     ExerciseMeasure measure, Muscle... muscles) {
        Exercise exercise = new Exercise(USER, name, category, Equipment.BARBELL, measure);
        exercise.updateMuscles(new LinkedHashSet<>(List.of(muscles)));
        ReflectionTestUtils.setField(exercise, "id", UUID.randomUUID());
        return exercise;
    }

    @Test
    void backSquatSuggestsSquatPatterns_neverAPlank() {
        Exercise backSquat = exercise("Back squat", ExerciseCategory.FORCE,
                ExerciseMeasure.WEIGHT_REPS, Muscle.QUADRICEPS, Muscle.FESSIERS);
        Exercise goblet = exercise("Goblet squat", ExerciseCategory.FORCE,
                ExerciseMeasure.WEIGHT_REPS, Muscle.QUADRICEPS, Muscle.FESSIERS);
        Exercise legPress = exercise("Presse à cuisses", ExerciseCategory.FORCE,
                ExerciseMeasure.WEIGHT_REPS, Muscle.QUADRICEPS, Muscle.FESSIERS);
        Exercise deadlift = exercise("Soulevé de terre", ExerciseCategory.FORCE,
                ExerciseMeasure.WEIGHT_REPS, Muscle.ISCHIOS, Muscle.FESSIERS, Muscle.DOS);
        Exercise plank = exercise("Planche", ExerciseCategory.GAINAGE,
                ExerciseMeasure.SECONDS, Muscle.CORE);
        Exercise curl = exercise("Curl biceps", ExerciseCategory.FORCE,
                ExerciseMeasure.WEIGHT_REPS, Muscle.BRAS);

        List<Exercise> suggested = AlternativeSuggester.suggest(backSquat,
                List.of(goblet, legPress, deadlift, plank, curl), Set.of(), 4);

        assertThat(suggested).extracting(Exercise::getName)
                .containsExactly("Goblet squat", "Presse à cuisses", "Soulevé de terre");
        // different category (plank) and no shared muscle (curl) never show up
    }

    @Test
    void excludedAndArchivedExercisesNeverSurface() {
        Exercise base = exercise("Back squat", ExerciseCategory.FORCE,
                ExerciseMeasure.WEIGHT_REPS, Muscle.QUADRICEPS, Muscle.FESSIERS);
        Exercise excluded = exercise("Goblet squat", ExerciseCategory.FORCE,
                ExerciseMeasure.WEIGHT_REPS, Muscle.QUADRICEPS, Muscle.FESSIERS);
        Exercise archived = exercise("Hack squat", ExerciseCategory.FORCE,
                ExerciseMeasure.WEIGHT_REPS, Muscle.QUADRICEPS);
        archived.updateArchived(true);

        List<Exercise> suggested = AlternativeSuggester.suggest(base,
                List.of(excluded, archived), Set.of(excluded.getId()), 4);

        assertThat(suggested).isEmpty();
    }
}
