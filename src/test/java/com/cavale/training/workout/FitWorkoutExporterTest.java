package com.cavale.training.workout;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.cavale.training.domain.Discipline;
import com.cavale.training.domain.PlanWeek;
import com.cavale.training.domain.PlannedSession;
import com.cavale.training.domain.TrainingPlan;
import com.cavale.training.domain.WeekType;

import static org.assertj.core.api.Assertions.assertThat;

class FitWorkoutExporterTest {

    private static PlannedSession session(String detail) {
        TrainingPlan plan = new TrainingPlan(UUID.randomUUID(), "Plan", null,
                LocalDate.of(2026, 7, 6), LocalDate.of(2026, 11, 29));
        PlanWeek week = new PlanWeek(plan, 5, LocalDate.of(2026, 8, 3), null,
                WeekType.BUILD, null, null, null, null);
        PlannedSession session = new PlannedSession(week, plan.getUserId(), LocalDate.of(2026, 8, 4),
                0, Discipline.RUN, "VMA — côtes courtes", detail, "VMA", 65, 250, 8, 8);
        ReflectionTestUtils.setField(session, "id", UUID.randomUUID());
        return session;
    }

    @Test
    void exportsValidFitFile() {
        String detail = """
                Échauffement : 20′ EF + 3 lignes droites.
                Corps : 2 × (8 × 30″ en côte 8–10 % à intensité VMA) ; R = 3′ entre séries.
                Retour au calme : 10′ EF.""";
        PlannedSession session = session(detail);

        byte[] fit = new FitWorkoutExporter().export(session, WorkoutParser.parse(detail));

        assertThat(fit.length).isGreaterThan(50);
        // FIT header: bytes 8..11 spell ".FIT"
        assertThat(new String(fit, 8, 4)).isEqualTo(".FIT");
    }

    @Test
    void exportsOpenStepWhenNothingParsed() {
        PlannedSession session = session(null);

        byte[] fit = new FitWorkoutExporter().export(session, WorkoutParser.parse(null));

        assertThat(new String(fit, 8, 4)).isEqualTo(".FIT");
    }
}
