package com.cavale.gym.service;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.cavale.gym.domain.Equipment;
import com.cavale.gym.domain.Exercise;
import com.cavale.gym.domain.ExerciseCategory;
import com.cavale.gym.domain.ExerciseMeasure;
import com.cavale.gym.service.WeightSuggester.PastSet;
import com.cavale.gym.service.WeightSuggester.Source;
import com.cavale.gym.service.WeightSuggester.Suggestion;

import static org.assertj.core.api.Assertions.assertThat;

class WeightSuggesterTest {

    private static final java.util.UUID USER = java.util.UUID.randomUUID();

    private static Exercise exercise(Equipment equipment, ExerciseMeasure measure) {
        return new Exercise(USER, "Squat", ExerciseCategory.FORCE, equipment, measure);
    }

    private static PastSet set(String weight, Integer reps) {
        return new PastSet(new BigDecimal(weight), reps, false);
    }

    @Test
    void intensityWins_andRoundsDownToALoadableBar() {
        Exercise squat = exercise(Equipment.BARBELL, ExerciseMeasure.WEIGHT_REPS);

        // 75 % of 110 = 82.5 exactly — a bar can make that
        Suggestion exact = WeightSuggester.suggest(squat, 75, 6, List.of(), new BigDecimal("110"));
        assertThat(exact.weightKg()).isEqualByComparingTo("82.5");
        assertThat(exact.source()).isEqualTo(Source.INTENSITY_OF_ONE_RM);
        assertThat(exact.basisKg()).isEqualByComparingTo("110");

        // 80 % of 107 = 85.6 — must land on 85, never 85.6 or 87.5
        Suggestion rounded = WeightSuggester.suggest(squat, 80, 5, List.of(), new BigDecimal("107"));
        assertThat(rounded.weightKg()).isEqualByComparingTo("85.0");
    }

    @Test
    void withoutAOneRepMax_theIntensityIsUnusableAndHistoryTakesOver() {
        Exercise squat = exercise(Equipment.BARBELL, ExerciseMeasure.WEIGHT_REPS);

        Suggestion s = WeightSuggester.suggest(squat, 75, 6,
                List.of(set("80", 6), set("80", 6), set("80", 6)), null);

        assertThat(s.source()).isEqualTo(Source.PROGRESSED_FROM_LAST);
        assertThat(s.weightKg()).isEqualByComparingTo("82.5"); // 80 + the barbell's 2.5
    }

    @Test
    void aSetThatFellShortHoldsTheLoad() {
        Exercise squat = exercise(Equipment.BARBELL, ExerciseMeasure.WEIGHT_REPS);

        Suggestion s = WeightSuggester.suggest(squat, null, 6,
                List.of(set("80", 6), set("80", 6), set("80", 5)), null);

        assertThat(s.source()).isEqualTo(Source.SAME_AS_LAST);
        assertThat(s.weightKg()).isEqualByComparingTo("80");
    }

    @Test
    void warmUpsNeverDecideTheWorkingLoad() {
        Exercise squat = exercise(Equipment.BARBELL, ExerciseMeasure.WEIGHT_REPS);

        Suggestion s = WeightSuggester.suggest(squat, null, 6, List.of(
                new PastSet(new BigDecimal("40"), 8, true),   // approach
                new PastSet(new BigDecimal("60"), 6, true),   // approach
                set("80", 6), set("80", 6)), null);

        // the 40 kg approach must not drag the proposal down, nor its 8 reps
        // count as clearing the target
        assertThat(s.source()).isEqualTo(Source.PROGRESSED_FROM_LAST);
        assertThat(s.weightKg()).isEqualByComparingTo("82.5");
    }

    @Test
    void theEquipmentSetsTheStep() {
        Exercise machine = exercise(Equipment.MACHINE, ExerciseMeasure.WEIGHT_REPS);

        Suggestion s = WeightSuggester.suggest(machine, null, 10,
                List.of(set("60", 10), set("60", 10)), null);

        assertThat(s.weightKg()).isEqualByComparingTo("65"); // a stack moves in 5 kg
    }

    @Test
    void aBrandNewLiftFallsBackToItsReferenceLoad() {
        Exercise squat = exercise(Equipment.BARBELL, ExerciseMeasure.WEIGHT_REPS);
        squat.updateLoading(null, new BigDecimal("61"));

        Suggestion s = WeightSuggester.suggest(squat, 75, 6, List.of(), null);

        assertThat(s.source()).isEqualTo(Source.REFERENCE);
        assertThat(s.weightKg()).isEqualByComparingTo("60.0"); // still floored to the bar
    }

    @Test
    void nothingToGoOn_staysEmptyRatherThanGuessing() {
        Exercise squat = exercise(Equipment.BARBELL, ExerciseMeasure.WEIGHT_REPS);

        Suggestion s = WeightSuggester.suggest(squat, null, 6, List.of(), null);

        assertThat(s.weightKg()).isNull();
        assertThat(s.source()).isEqualTo(Source.NONE);
    }

    @Test
    void aHoldIsNotALoad() {
        Exercise plank = exercise(Equipment.BODYWEIGHT, ExerciseMeasure.SECONDS);

        Suggestion s = WeightSuggester.suggest(plank, 75, null, List.of(set("10", 1)),
                new BigDecimal("100"));

        assertThat(s.weightKg()).isNull();
    }

    @Test
    void reserveMakesTheOneRepMaxHonest() {
        // same set, three reps left in the tank: evidence of a bigger max
        BigDecimal atFailure = OneRepMax.estimate(new BigDecimal("100"), 5, 0);
        BigDecimal withReserve = OneRepMax.estimate(new BigDecimal("100"), 5, 3);

        assertThat(atFailure).isEqualByComparingTo("116.7");
        assertThat(withReserve).isEqualByComparingTo("126.7");

        // an unrated set is read at face value — the conservative reading
        assertThat(OneRepMax.estimate(new BigDecimal("100"), 5, null))
                .isEqualByComparingTo(atFailure);
    }
}
