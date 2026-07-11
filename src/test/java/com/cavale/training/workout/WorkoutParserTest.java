package com.cavale.training.workout;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.cavale.training.workout.WorkoutStructure.Allure;
import com.cavale.training.workout.WorkoutStructure.Node;
import com.cavale.training.workout.WorkoutStructure.Parsed;
import com.cavale.training.workout.WorkoutStructure.Terrain;

import static org.assertj.core.api.Assertions.assertThat;

class WorkoutParserTest {

    @Test
    void nestedIntervalsWithDeterministicRecoveries() {
        String detail = """
                Échauffement : 20′ EF + 3 lignes droites.
                Corps : 2 × (8 × 30″ en côte 8–10 % à intensité VMA — effort 9/10, récup = descente en trot) ; R = 3′ entre séries.
                Retour au calme : 10′ EF.""";

        List<Node> nodes = WorkoutParser.parse(detail, "VMA", 65).nodes();

        // warmup: EF block + strides loop (ALWAYS ≥ 2 blocks: sprint + récup)
        assertThat(nodes.get(0).allure()).isEqualTo(Allure.EF);
        assertThat(nodes.get(0).seconds()).isEqualTo(1200);
        Node strides = nodes.get(1);
        assertThat(strides.count()).isEqualTo(3);
        assertThat(strides.children()).extracting(Node::allure)
                .containsExactly(Allure.SPRINT, Allure.LENTE);

        // main: 2 × [ 8 × [30″ VMA côte + récup LENTE] + 3 min LENTE ]
        Node outer = nodes.get(2);
        assertThat(outer.count()).isEqualTo(2);
        Node inner = outer.children().get(0);
        assertThat(inner.count()).isEqualTo(8);
        assertThat(inner.children().get(0).allure()).isEqualTo(Allure.VMA);
        assertThat(inner.children().get(0).seconds()).isEqualTo(30);
        assertThat(inner.children().get(0).terrain()).isEqualTo(Terrain.COTE);
        assertThat(inner.children().get(1).allure()).isEqualTo(Allure.LENTE);
        assertThat(inner.children().get(1).seconds()).isNotNull(); // deterministic default
        assertThat(outer.children().get(1).allure()).isEqualTo(Allure.LENTE);
        assertThat(outer.children().get(1).seconds()).isEqualTo(180);

        // cooldown: EF stated explicitly
        assertThat(nodes.get(3).seconds()).isEqualTo(600);
    }

    @Test
    void proseOnlyDetailSynthesizesFromSessionZoneAndDuration() {
        // "Reprise footing 30′" — the detail is pure instructions
        Parsed parsed = WorkoutParser.parse(
                "SI ET SEULEMENT SI feu vert du chirurgien : footing très plat, bandeau anti-sueur.",
                "Récup", 30);

        assertThat(parsed.nodes()).hasSize(1);
        assertThat(parsed.nodes().getFirst().allure()).isEqualTo(Allure.LENTE);
        assertThat(parsed.nodes().getFirst().seconds()).isEqualTo(1800);
        assertThat(parsed.notes()).contains("feu vert du chirurgien");
    }

    @Test
    void longRunSynthesizesEfNeverOpen() {
        // "SL trail 1h30" — detail has instructions but no explicit structure
        Parsed parsed = WorkoutParser.parse(
                "Sortie longue trail EF STRICT, 400–500 D+. Boire 500 ml/h. Descentes en souplesse.",
                "EF", 90);

        List<Node> nodes = parsed.nodes();
        assertThat(nodes).isNotEmpty();
        assertThat(nodes.getFirst().allure()).isEqualTo(Allure.EF);
        assertThat(nodes.getFirst().seconds()).isNotNull();
    }

    @Test
    void lthrTestGetsSeuil30AndCooldownGetsLente() {
        String detail = """
                Échauffement : 20′ EF progressif + 3 lignes droites.
                Corps : 30′ à intensité MAXIMALE que tu peux tenir RÉGULIÈREMENT sur 30′ (seul, à plat, parcours mesuré).
                Retour au calme : 10′ très facile.""";

        List<Node> nodes = WorkoutParser.parse(detail, "Test", 60).nodes();

        Node test = nodes.get(2);
        assertThat(test.allure()).isEqualTo(Allure.SEUIL30);
        assertThat(test.seconds()).isEqualTo(1800);

        Node cooldown = nodes.getLast();
        assertThat(cooldown.allure()).isEqualTo(Allure.LENTE);
        assertThat(cooldown.seconds()).isEqualTo(600);
    }

    @Test
    void thirtyThirtyAlternation() {
        List<Node> nodes = WorkoutParser.parse("Corps : 6×30/30 à VMA.", "VMA", 40).nodes();

        // the session time not covered by the intervals becomes a leading EF block
        assertThat(nodes.getFirst().allure()).isEqualTo(Allure.EF);
        Node repeat = nodes.get(1);
        assertThat(repeat.count()).isEqualTo(6);
        assertThat(repeat.children()).extracting(Node::allure)
                .containsExactly(Allure.VMA, Allure.LENTE);
    }

    @Test
    void emptyEverythingGivesNoNodes() {
        assertThat(WorkoutParser.parse(null, null, null).nodes()).isEmpty();
    }
}
