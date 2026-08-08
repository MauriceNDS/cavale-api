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
                - Allure Seuil 60 (1/3) 8m 94-100% Pace
                - Récup (1/3) 3m
                - Allure Seuil 60 (2/3) 8m 94-100% Pace
                - Récup (2/3) 3m
                - Allure Seuil 60 (3/3) 8m 94-100% Pace
                - Récup (3/3) 3m
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

    /**
     * The session the athlete could not follow: two identical 8×30s blocks.
     * Every rep now names its own position, and the two séries are told apart.
     */
    @Test
    void twoIdenticalSeriesAreTellableApartRepByRep() {
        Node block = Node.repeat(3, List.of(
                Node.step(Allure.VMA, 30, null),
                Node.step(Allure.LENTE, 60, null)));
        String text = mapper.describe(List.of(block, Node.step(Allure.LENTE, 120, null), block));

        assertThat(text).isEqualTo("""
                - Allure VMA (S1 1/3) 30s 110-120% Pace
                - Récup (S1 1/3) 1m
                - Allure VMA (S1 2/3) 30s 110-120% Pace
                - Récup (S1 2/3) 1m
                - Allure VMA (S1 3/3) 30s 110-120% Pace
                - Récup (S1 3/3) 1m
                - Récup 2m
                - Allure VMA (S2 1/3) 30s 110-120% Pace
                - Récup (S2 1/3) 1m
                - Allure VMA (S2 2/3) 30s 110-120% Pace
                - Récup (S2 2/3) 1m
                - Allure VMA (S2 3/3) 30s 110-120% Pace
                - Récup (S2 3/3) 1m""");
    }

    /** An outer loop repeats its inner série, which keeps counting up. */
    @Test
    void nestedRepeatsNumberEachInnerSeries() {
        List<Node> nodes = List.of(
                Node.repeat(2, List.of(
                        Node.repeat(2, List.of(
                                Node.step(Allure.VMA, 30, null),
                                Node.step(Allure.LENTE, 60, null))),
                        Node.step(Allure.LENTE, 180, null))));

        String text = mapper.describe(nodes);

        assertThat(text).isEqualTo("""
                - Allure VMA (S1 1/2) 30s 110-120% Pace
                - Récup (S1 1/2) 1m
                - Allure VMA (S1 2/2) 30s 110-120% Pace
                - Récup (S1 2/2) 1m
                - Récup 3m
                - Allure VMA (S2 1/2) 30s 110-120% Pace
                - Récup (S2 1/2) 1m
                - Allure VMA (S2 2/2) 30s 110-120% Pace
                - Récup (S2 2/2) 1m
                - Récup 3m""");
    }

    @Test
    void emptyTreeRendersEmpty() {
        assertThat(mapper.describe(List.of())).isEmpty();
    }
}
