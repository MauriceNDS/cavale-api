package com.cavale.integration.intervals;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.cavale.training.workout.WorkoutStructure.Allure;
import com.cavale.training.workout.WorkoutStructure.Node;
import com.cavale.training.workout.WorkoutStructure.Terrain;

import static org.assertj.core.api.Assertions.assertThat;

class IntervalsWorkoutMapperTest {

    private final IntervalsWorkoutMapper mapper = new IntervalsWorkoutMapper();

    @Test
    void rendersStepsWithDurationsAndPaceTargets() {
        // 20′ EF + 3×(8′ seuil 60 + 3′ récup) + 10′ récup
        List<Node> nodes = List.of(
                Node.step(Allure.EF, 1200, null),
                Node.repeat(3, List.of(
                        Node.step(Allure.SEUIL60, 480, null),
                        Node.step(Allure.LENTE, 180, null))),
                Node.step(Allure.LENTE, 600, null));

        String text = mapper.describe(nodes);

        assertThat(text).isEqualTo("""
                - Allure EF 20m 65-78% Pace

                3x
                - Allure Seuil 60 8m 94-100% Pace
                - Récup 3m

                - Récup 10m""");
    }

    @Test
    void recoveryStepsCarryNoTarget() {
        String text = mapper.describe(List.of(Node.step(Allure.LENTE, 300, null)));

        assertThat(text).isEqualTo("- Récup 5m");
    }

    @Test
    void terrainBecomesPartOfTheCue() {
        String text = mapper.describe(List.of(
                Node.step(Allure.VMA, 30, Terrain.COTE),
                Node.step(Allure.EF, 90, Terrain.DESCENTE)));

        assertThat(text).contains("- Allure VMA (côte) 30s 110-120% Pace")
                .contains("- Allure EF (descente) 1m30s 65-78% Pace");
    }

    /** Intervals.icu loops can't nest — the inner loop is unrolled in place. */
    @Test
    void nestedRepeatsAreUnrolled() {
        List<Node> nodes = List.of(
                Node.repeat(2, List.of(
                        Node.repeat(2, List.of(
                                Node.step(Allure.VMA, 30, null),
                                Node.step(Allure.LENTE, 60, null))),
                        Node.step(Allure.LENTE, 180, null))));

        String text = mapper.describe(nodes);

        assertThat(text).isEqualTo("""
                2x
                - Allure VMA 30s 110-120% Pace
                - Récup 1m
                - Allure VMA 30s 110-120% Pace
                - Récup 1m
                - Récup 3m""");
    }

    @Test
    void emptyTreeRendersEmpty() {
        assertThat(mapper.describe(List.of())).isEmpty();
    }
}
