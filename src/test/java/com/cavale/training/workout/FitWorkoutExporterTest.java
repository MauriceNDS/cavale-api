package com.cavale.training.workout;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.cavale.training.domain.Discipline;
import com.cavale.training.domain.PlanWeek;
import com.cavale.training.domain.PlannedSession;
import com.cavale.training.domain.TrainingPlan;
import com.cavale.training.domain.WeekType;
import com.cavale.training.workout.WorkoutStructure.Allure;
import com.cavale.training.workout.WorkoutStructure.Node;

import static org.assertj.core.api.Assertions.assertThat;

class FitWorkoutExporterTest {

    private static PlannedSession session() {
        TrainingPlan plan = new TrainingPlan(UUID.randomUUID(), "Plan", null,
                LocalDate.of(2026, 7, 6), LocalDate.of(2026, 11, 29));
        PlanWeek week = new PlanWeek(plan, 5, LocalDate.of(2026, 8, 3), null,
                WeekType.BUILD, null, null, null, null);
        PlannedSession session = new PlannedSession(week, plan.getUserId(), LocalDate.of(2026, 8, 4),
                0, Discipline.RUN, "VMA — côtes courtes", null, "VMA", 65, 250, 8, 8);
        ReflectionTestUtils.setField(session, "id", UUID.randomUUID());
        return session;
    }

    @Test
    void exportsValidFitFileFromWorkoutTree() {
        List<Node> nodes = List.of(
                Node.step(Allure.EF, 1200, null),
                Node.repeat(2, List.of(
                        Node.repeat(8, List.of(
                                Node.step(Allure.VMA, 30, WorkoutStructure.Terrain.COTE),
                                Node.step(Allure.LENTE, 60, null))),
                        Node.step(Allure.LENTE, 180, null))),
                Node.step(Allure.LENTE, 600, null));

        byte[] fit = new FitWorkoutExporter().export(session(), nodes);

        assertThat(fit.length).isGreaterThan(50);
        assertThat(new String(fit, 8, 4)).isEqualTo(".FIT"); // FIT header magic
    }

    /**
     * Round-trip through the FIT SDK decoder: Garmin must see a WORKOUT file
     * (not a course/activity) whose steps carry the exact timings, and in
     * which every rep of every série arrives already numbered — the watch has
     * no other way to say whether this is the fifth or the seventh.
     */
    @Test
    void decodesAsAStructuredWorkoutWithEveryRepNumbered() {
        // 20′ EF + 3×(20″ sprint + 1′ récup) + 2×(8×(30″ VMA côte + 1′ récup) + 3′ récup) + 10′ récup
        List<Node> nodes = List.of(
                Node.step(Allure.EF, 1200, null),
                Node.repeat(3, List.of(
                        Node.step(Allure.SPRINT, 20, null),
                        Node.step(Allure.LENTE, 60, null))),
                Node.repeat(2, List.of(
                        Node.repeat(8, List.of(
                                Node.step(Allure.VMA, 30, WorkoutStructure.Terrain.COTE),
                                Node.step(Allure.LENTE, 60, null))),
                        Node.step(Allure.LENTE, 180, null))),
                Node.step(Allure.LENTE, 600, null));

        byte[] fit = new FitWorkoutExporter().export(session(), nodes);

        java.util.concurrent.atomic.AtomicReference<com.garmin.fit.FileIdMesg> fileId =
                new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<com.garmin.fit.WorkoutMesg> workout =
                new java.util.concurrent.atomic.AtomicReference<>();
        List<com.garmin.fit.WorkoutStepMesg> steps = new java.util.ArrayList<>();

        com.garmin.fit.Decode decode = new com.garmin.fit.Decode();
        com.garmin.fit.MesgBroadcaster broadcaster = new com.garmin.fit.MesgBroadcaster(decode);
        broadcaster.addListener((com.garmin.fit.FileIdMesgListener) fileId::set);
        broadcaster.addListener((com.garmin.fit.WorkoutMesgListener) workout::set);
        broadcaster.addListener((com.garmin.fit.WorkoutStepMesgListener) steps::add);
        decode.read(new java.io.ByteArrayInputStream(fit), broadcaster, broadcaster);

        assertThat(fileId.get().getType()).isEqualTo(com.garmin.fit.File.WORKOUT);
        assertThat(workout.get().getSport()).isEqualTo(com.garmin.fit.Sport.RUNNING);
        assertThat(workout.get().getNumValidSteps()).isEqualTo(steps.size());

        // Nothing is left for the watch to expand — every rep is its own step.
        assertThat(steps).noneMatch(s -> s.getDurationType()
                == com.garmin.fit.WktStepDuration.REPEAT_UNTIL_STEPS_CMPLT);
        // 1 EF + 3×2 sprint série + 2×(8×2 VMA série + 1 récup) + 1 récup
        assertThat(steps).hasSize(42);

        // 20′ EF warm-up: timed step, milliseconds, outside any série so unlabelled
        assertThat(steps.get(0).getDurationType()).isEqualTo(com.garmin.fit.WktStepDuration.TIME);
        assertThat(steps.get(0).getDurationValue()).isEqualTo(1_200_000L);
        assertThat(steps.get(0).getWktStepName()).isEqualTo("Allure EF");

        // 3×(20″ sprint + 1′ récup) — the first série, unrolled rep by rep
        assertThat(steps.get(1).getWktStepName()).isEqualTo("Allure Sprint (S1 1/3)");
        assertThat(steps.get(1).getDurationValue()).isEqualTo(20_000L);
        assertThat(steps.get(2).getWktStepName()).isEqualTo("Récup (S1 1/3)");
        assertThat(steps.get(2).getIntensity()).isEqualTo(com.garmin.fit.Intensity.REST);
        assertThat(steps.get(5).getWktStepName()).isEqualTo("Allure Sprint (S1 3/3)");

        // 2×(8×(30″ VMA côte + 1′ récup) + 3′): each pass through the inner
        // série gets its own number, so the two halves never look alike
        assertThat(steps.get(7).getWktStepName()).isEqualTo("Allure VMA (côte) (S2 1/8)");
        assertThat(steps.get(7).getDurationValue()).isEqualTo(30_000L);
        assertThat(steps.get(22).getWktStepName()).isEqualTo("Récup (S2 8/8)");
        assertThat(steps.get(23).getWktStepName()).isEqualTo("Récup"); // the 3′ between séries
        assertThat(steps.get(24).getWktStepName()).isEqualTo("Allure VMA (côte) (S3 1/8)");
        assertThat(steps.get(39).getWktStepName()).isEqualTo("Récup (S3 8/8)");

        // final 10′ récup: REST, timed
        var last = steps.get(41);
        assertThat(last.getDurationValue()).isEqualTo(600_000L);
        assertThat(last.getIntensity()).isEqualTo(com.garmin.fit.Intensity.REST);
    }

    @Test
    void exportsOpenStepWhenNoStructure() {
        byte[] fit = new FitWorkoutExporter().export(session(), List.of());

        assertThat(new String(fit, 8, 4)).isEqualTo(".FIT");
    }
}
