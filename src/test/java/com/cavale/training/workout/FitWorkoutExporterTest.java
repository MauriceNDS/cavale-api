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
     * (not a course/activity) whose steps carry the exact timings and whose
     * repeat structures point at the right first step with the right count.
     */
    @Test
    void decodesAsAStructuredWorkoutWithTimingsAndNestedRepeats() {
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

        // 20′ EF warm-up: timed step, milliseconds
        assertThat(steps.get(0).getDurationType()).isEqualTo(com.garmin.fit.WktStepDuration.TIME);
        assertThat(steps.get(0).getDurationValue()).isEqualTo(1_200_000L);

        // 3×(20″ sprint + 1′ récup): steps 1-2 then the repeat pointing back at 1
        assertThat(steps.get(1).getDurationValue()).isEqualTo(20_000L);
        assertThat(steps.get(2).getIntensity()).isEqualTo(com.garmin.fit.Intensity.REST);
        var firstRepeat = steps.get(3);
        assertThat(firstRepeat.getDurationType())
                .isEqualTo(com.garmin.fit.WktStepDuration.REPEAT_UNTIL_STEPS_CMPLT);
        assertThat(firstRepeat.getDurationValue()).isEqualTo(1L);
        assertThat(firstRepeat.getTargetValue()).isEqualTo(3L);

        // nested 2×(8×(…)+3′): inner repeat closes over steps 4-5, outer over 4-7
        var innerRepeat = steps.get(6);
        assertThat(innerRepeat.getDurationValue()).isEqualTo(4L);
        assertThat(innerRepeat.getTargetValue()).isEqualTo(8L);
        var outerRepeat = steps.get(8);
        assertThat(outerRepeat.getDurationValue()).isEqualTo(4L);
        assertThat(outerRepeat.getTargetValue()).isEqualTo(2L);

        // final 10′ récup: REST, timed
        var last = steps.get(9);
        assertThat(last.getDurationValue()).isEqualTo(600_000L);
        assertThat(last.getIntensity()).isEqualTo(com.garmin.fit.Intensity.REST);
        assertThat(steps).hasSize(10);
    }

    @Test
    void exportsOpenStepWhenNoStructure() {
        byte[] fit = new FitWorkoutExporter().export(session(), List.of());

        assertThat(new String(fit, 8, 4)).isEqualTo(".FIT");
    }
}
