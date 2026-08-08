package com.cavale.training.workout;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.cavale.training.workout.WorkoutStructure.Allure;
import com.cavale.training.workout.WorkoutStructure.Node;

import static org.assertj.core.api.Assertions.assertThat;

class WorkoutFlattenerTest {

    private static List<String> labels(List<Node> nodes) {
        return WorkoutFlattener.flatten(nodes).stream().map(WorkoutFlattener.FlatStep::repLabel).toList();
    }

    @Test
    void stepsOutsideAnyRepeatCarryNoLabel() {
        List<Node> nodes = List.of(
                Node.step(Allure.EF, 1200, null),
                Node.step(Allure.LENTE, 600, null));

        assertThat(labels(nodes)).containsExactly(null, null);
    }

    @Test
    void aLoneSeriesNumbersItsRepsWithoutASeriesPrefix() {
        List<Node> nodes = List.of(Node.repeat(3, List.of(
                Node.step(Allure.VMA, 30, null),
                Node.step(Allure.LENTE, 60, null))));

        assertThat(labels(nodes)).containsExactly("(1/3)", "(1/3)", "(2/3)", "(2/3)", "(3/3)", "(3/3)");
    }

    @Test
    void severalSeriesGetTheirOwnNumber() {
        Node block = Node.repeat(2, List.of(Node.step(Allure.VMA, 30, null)));
        List<Node> nodes = List.of(block, Node.step(Allure.LENTE, 120, null), block);

        assertThat(labels(nodes)).containsExactly("(S1 1/2)", "(S1 2/2)", null, "(S2 1/2)", "(S2 2/2)");
    }

    @Test
    void anOuterLoopKeepsTheInnerSeriesCounting() {
        List<Node> nodes = List.of(Node.repeat(3, List.of(
                Node.repeat(2, List.of(Node.step(Allure.VMA, 30, null))))));

        assertThat(labels(nodes))
                .containsExactly("(S1 1/2)", "(S1 2/2)", "(S2 1/2)", "(S2 2/2)", "(S3 1/2)", "(S3 2/2)");
    }

    @Test
    void unrollingPreservesTheOrderAndTheSteps() {
        List<Node> nodes = List.of(
                Node.step(Allure.EF, 600, null),
                Node.repeat(2, List.of(
                        Node.step(Allure.VMA, 30, null),
                        Node.step(Allure.LENTE, 60, null))));

        assertThat(WorkoutFlattener.flatten(nodes).stream().map(s -> s.node().allure()).toList())
                .containsExactly(Allure.EF, Allure.VMA, Allure.LENTE, Allure.VMA, Allure.LENTE);
    }

    @Test
    void anEmptyTreeFlattensToNothing() {
        assertThat(WorkoutFlattener.flatten(List.of())).isEmpty();
    }
}
