package com.cavale.training.workout;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.cavale.training.workout.WorkoutStructure.Block;
import com.cavale.training.workout.WorkoutStructure.Section;
import com.cavale.training.workout.WorkoutStructure.Step;

import static org.assertj.core.api.Assertions.assertThat;

class WorkoutParserTest {

    @Test
    void parsesCampusFormatWithIntervals() {
        String detail = """
                Échauffement : 20′ EF + 3 lignes droites.
                Corps : 2 × (8 × 30″ en côte 8–10 % à intensité VMA — effort 9/10, récup = descente en trot) ; R = 3′ entre séries.
                Retour au calme : 10′ EF.""";

        List<Block> blocks = WorkoutParser.parse(detail);

        assertThat(blocks).extracting(Block::section)
                .containsExactly(Section.WARMUP, Section.MAIN, Section.COOLDOWN);

        Block warmup = blocks.get(0);
        assertThat(warmup.steps()).hasSize(2);
        assertThat(warmup.steps().get(0).durationSec()).isEqualTo(1200);
        assertThat(warmup.steps().get(0).zone()).isEqualTo("EF");
        assertThat(warmup.steps().get(1).repeats()).isEqualTo(3);
        assertThat(warmup.steps().get(1).zone()).isEqualTo("Sprint");

        Step intervals = blocks.get(1).steps().get(0);
        assertThat(intervals.repeats()).isEqualTo(16);
        assertThat(intervals.repeatLabel()).isEqualTo("2 × 8");
        assertThat(intervals.durationSec()).isEqualTo(30);
        assertThat(intervals.zone()).isEqualTo("VMA");

        Step cooldown = blocks.get(2).steps().get(0);
        assertThat(cooldown.durationSec()).isEqualTo(600);
        assertThat(cooldown.zone()).isEqualTo("EF");
    }

    @Test
    void plainTextBecomesSingleMainBlock() {
        List<Block> blocks = WorkoutParser.parse("EF souple, terrain roulant. Boire 500 ml/h.");

        assertThat(blocks).hasSize(1);
        assertThat(blocks.getFirst().section()).isEqualTo(Section.MAIN);
        assertThat(blocks.getFirst().steps().getFirst().zone()).isEqualTo("EF");
    }

    @Test
    void parsesHoursAndPuisSplits() {
        List<Block> blocks = WorkoutParser.parse(
                "Échauffement : —\nCorps : 1h EF, puis 8 × 80–100 m progressives, retour marche.\nRetour au calme : 5′ marche");

        Block main = blocks.stream().filter(b -> b.section() == Section.MAIN).findFirst().orElseThrow();
        assertThat(main.steps().get(0).durationSec()).isEqualTo(3600);
        assertThat(main.steps().get(1).repeats()).isEqualTo(8);
    }

    @Test
    void emptyDetailGivesNoBlocks() {
        assertThat(WorkoutParser.parse(null)).isEmpty();
        assertThat(WorkoutParser.parse("  ")).isEmpty();
    }
}
