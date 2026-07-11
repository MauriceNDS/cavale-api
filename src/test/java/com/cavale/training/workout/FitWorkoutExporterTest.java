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

    @Test
    void exportsOpenStepWhenNoStructure() {
        byte[] fit = new FitWorkoutExporter().export(session(), List.of());

        assertThat(new String(fit, 8, 4)).isEqualTo(".FIT");
    }
}
