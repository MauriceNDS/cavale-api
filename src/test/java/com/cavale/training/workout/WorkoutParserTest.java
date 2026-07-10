package com.cavale.training.workout;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.cavale.training.workout.WorkoutStructure.Block;
import com.cavale.training.workout.WorkoutStructure.Node;
import com.cavale.training.workout.WorkoutStructure.Section;

import static org.assertj.core.api.Assertions.assertThat;

class WorkoutParserTest {

    @Test
    void parsesNestedIntervalsIntoRepeatTree() {
        String detail = """
                Échauffement : 20′ EF + 3 lignes droites.
                Corps : 2 × (8 × 30″ en côte 8–10 % à intensité VMA — effort 9/10, récup = descente en trot) ; R = 3′ entre séries.
                Retour au calme : 10′ EF.""";

        List<Block> blocks = WorkoutParser.parse(detail);

        assertThat(blocks).extracting(Block::section)
                .containsExactly(Section.WARMUP, Section.MAIN, Section.COOLDOWN);

        // Warmup: 20′ EF step + strides as a repeat group of 3
        List<Node> warmup = blocks.get(0).nodes();
        assertThat(warmup.get(0).durationSec()).isEqualTo(1200);
        assertThat(warmup.get(0).zone()).isEqualTo("EF");
        assertThat(warmup.get(1).isRepeat()).isTrue();
        assertThat(warmup.get(1).count()).isEqualTo(3);
        assertThat(warmup.get(1).children().getFirst().zone()).isEqualTo("Sprint");

        // Main: outer repeat ×2 [ inner repeat ×8 [work 30″ VMA, récup open], R=3′ ]
        List<Node> main = blocks.get(1).nodes();
        assertThat(main).hasSize(1);
        Node outer = main.getFirst();
        assertThat(outer.isRepeat()).isTrue();
        assertThat(outer.count()).isEqualTo(2);
        assertThat(outer.children()).hasSize(2);

        Node inner = outer.children().get(0);
        assertThat(inner.isRepeat()).isTrue();
        assertThat(inner.count()).isEqualTo(8);
        assertThat(inner.children().get(0).durationSec()).isEqualTo(30);
        assertThat(inner.children().get(0).zone()).isEqualTo("VMA");
        assertThat(inner.children().get(1).zone()).isEqualTo("Récup");

        Node seriesRecovery = outer.children().get(1);
        assertThat(seriesRecovery.durationSec()).isEqualTo(180);

        // Cooldown
        assertThat(blocks.get(2).nodes().getFirst().durationSec()).isEqualTo(600);
    }

    @Test
    void parsesThirtyThirty() {
        List<Block> blocks = WorkoutParser.parse("Corps : 6×30/30 à VMA.");

        Node repeat = blocks.getFirst().nodes().getFirst();
        assertThat(repeat.isRepeat()).isTrue();
        assertThat(repeat.count()).isEqualTo(6);
        assertThat(repeat.children().get(0).durationSec()).isEqualTo(30);
        assertThat(repeat.children().get(1).durationSec()).isEqualTo(30);
        assertThat(repeat.children().get(1).zone()).isEqualTo("Récup");
    }

    @Test
    void plainTextBecomesSingleMainStep() {
        List<Block> blocks = WorkoutParser.parse("EF souple, terrain roulant. Boire 500 ml/h.");

        assertThat(blocks).hasSize(1);
        assertThat(blocks.getFirst().section()).isEqualTo(Section.MAIN);
        Node step = blocks.getFirst().nodes().getFirst();
        assertThat(step.isRepeat()).isFalse();
        assertThat(step.zone()).isEqualTo("EF");
    }

    @Test
    void parsesHoursAndPuisSplits() {
        List<Block> blocks = WorkoutParser.parse(
                "Échauffement : —\nCorps : 1h EF, puis 8 × 80–100 m progressives, retour marche.\nRetour au calme : 5′ marche");

        Block main = blocks.stream().filter(b -> b.section() == Section.MAIN).findFirst().orElseThrow();
        assertThat(main.nodes().get(0).durationSec()).isEqualTo(3600);
        assertThat(main.nodes().get(1).isRepeat()).isTrue();
        assertThat(main.nodes().get(1).count()).isEqualTo(8);
    }

    @Test
    void emptyDetailGivesNoBlocks() {
        assertThat(WorkoutParser.parse(null)).isEmpty();
        assertThat(WorkoutParser.parse("  ")).isEmpty();
    }
}
