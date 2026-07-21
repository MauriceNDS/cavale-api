package com.cavale.training.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.cavale.training.domain.Activity;
import com.cavale.training.domain.ActivitySource;
import com.cavale.training.domain.Discipline;
import com.cavale.training.domain.Objective;
import com.cavale.training.domain.ObjectiveRole;
import com.cavale.training.domain.ObjectiveType;
import com.cavale.training.domain.PlanWeek;
import com.cavale.training.domain.PlannedSession;
import com.cavale.training.domain.SessionStatus;
import com.cavale.training.domain.TrainingPlan;
import com.cavale.training.domain.WeekType;
import com.cavale.training.dto.PlanProgressResponse;
import com.cavale.training.dto.PlanProgressResponse.WeekProgress;
import com.cavale.training.repository.ActivityRepository;
import com.cavale.training.repository.ObjectiveRepository;
import com.cavale.training.repository.PlanWeekRepository;
import com.cavale.training.repository.PlannedSessionRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanProgressServiceTest {

    @Mock
    private TrainingPlanService planService;

    @Mock
    private PlanWeekRepository weekRepository;

    @Mock
    private PlannedSessionRepository sessionRepository;

    @Mock
    private ActivityRepository activityRepository;

    @Mock
    private ObjectiveRepository objectiveRepository;

    @Mock
    private com.cavale.training.pace.PaceModelService paceModelService;

    private PlanProgressService service() {
        when(paceModelService.modelFor(OWNER)).thenReturn(com.cavale.training.pace.PaceModel.fallback());
        return new PlanProgressService(planService, weekRepository, sessionRepository,
                activityRepository, objectiveRepository, paceModelService);
    }

    private static final UUID OWNER = UUID.randomUUID();
    /** Thursday of week 2 — three days of that week have elapsed. */
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 9);

    private static <T> T withId(T entity) {
        ReflectionTestUtils.setField(entity, "id", UUID.randomUUID());
        return entity;
    }

    private static PlannedSession session(PlanWeek week, LocalDate date, Discipline discipline,
                                          Integer durationMin, SessionStatus status) {
        PlannedSession session = withId(new PlannedSession(week, OWNER, date, 0, discipline,
                "Séance", null, null, durationMin, null, null, null));
        session.updateStatus(status);
        return session;
    }

    private static Activity activity(PlannedSession session, String distanceKm, Integer elevationM, int durationMin) {
        return withId(new Activity(session, ActivitySource.MANUAL, session.getDate(), durationMin,
                new BigDecimal(distanceKm), elevationM, null, null));
    }

    @Test
    void getProgress_aggregatesTargetsAndActualsPerWeekAndToDate() {
        TrainingPlan plan = withId(new TrainingPlan(OWNER, "Plan", "Course test",
                LocalDate.of(2026, 6, 29), LocalDate.of(2026, 7, 19)));
        PlanWeek week1 = withId(new PlanWeek(plan, 1, LocalDate.of(2026, 6, 29), "Prépa",
                WeekType.BUILD, new BigDecimal("40.0"), 1000, 300, null));
        PlanWeek week2 = withId(new PlanWeek(plan, 2, LocalDate.of(2026, 7, 6), "Prépa",
                WeekType.BUILD, new BigDecimal("50.0"), 1200, 350, null));

        PlannedSession run1 = session(week1, LocalDate.of(2026, 6, 30), Discipline.RUN, 60, SessionStatus.DONE);
        PlannedSession gym1 = session(week1, LocalDate.of(2026, 7, 1), Discipline.GYM, 50, SessionStatus.DONE);
        PlannedSession skipped1 = session(week1, LocalDate.of(2026, 7, 3), Discipline.RUN, 45, SessionStatus.SKIPPED);
        PlannedSession rest1 = session(week1, LocalDate.of(2026, 7, 5), Discipline.REST, null, SessionStatus.PLANNED);
        PlannedSession run2 = session(week2, LocalDate.of(2026, 7, 7), Discipline.RUN, 70, SessionStatus.DONE);
        PlannedSession future2 = session(week2, LocalDate.of(2026, 7, 10), Discipline.RUN, 80, SessionStatus.PLANNED);

        Activity activityRun1 = activity(run1, "10.50", 400, 65);
        Activity activityRun2 = activity(run2, "12.00", 300, 70);

        Objective main = withId(new Objective(plan, ObjectiveRole.MAIN, ObjectiveType.RACE,
                "Course test", LocalDate.of(2026, 7, 19)));
        Objective secondary = withId(new Objective(plan, ObjectiveRole.SECONDARY, ObjectiveType.RACE,
                "Course prépa", LocalDate.of(2026, 7, 12)));

        when(planService.getOwnedPlan(OWNER, plan.getId())).thenReturn(plan);
        when(weekRepository.findByPlanIdOrderByWeekNumber(plan.getId())).thenReturn(List.of(week1, week2));
        when(sessionRepository.findByWeekPlanId(plan.getId()))
                .thenReturn(List.of(run1, gym1, skipped1, rest1, run2, future2));
        when(activityRepository.findBySessionIdIn(anyList())).thenReturn(List.of(activityRun1, activityRun2));
        when(objectiveRepository.findByPlanId(plan.getId())).thenReturn(List.of(main, secondary));

        PlanProgressResponse progress = service().getProgress(OWNER, plan.getId(), TODAY);

        assertThat(progress.mainObjective().name()).isEqualTo("Course test");
        assertThat(progress.secondaryObjectives()).hasSize(1);
        assertThat(progress.totalWeeks()).isEqualTo(2);
        assertThat(progress.currentWeekNumber()).isEqualTo(2);
        assertThat(progress.daysToObjective()).isEqualTo(10);

        // REST days never count as sessions
        assertThat(progress.totals().sessionsPlanned()).isEqualTo(5);
        assertThat(progress.totals().sessionsDone()).isEqualTo(3);
        assertThat(progress.totals().sessionsSkipped()).isEqualTo(1);
        // adherence: 4 sessions are in the past, 3 of them done
        assertThat(progress.totals().sessionsDuePast()).isEqualTo(4);
        assertThat(progress.totals().sessionsDonePast()).isEqualTo(3);

        assertThat(progress.totals().actualVolumeKm()).isEqualByComparingTo("22.50");
        assertThat(progress.totals().actualElevationM()).isEqualTo(700);
        // 65 + 70 from activities, 50 from the DONE gym session without one
        assertThat(progress.totals().actualDurationMin()).isEqualTo(185);

        // week 1 counts in full, week 2 prorated at 3/7
        assertThat(progress.totals().plannedVolumeKmToDate()).isEqualByComparingTo("61.4");
        assertThat(progress.totals().plannedElevationMToDate()).isEqualTo(1514);
        assertThat(progress.totals().targetVolumeKm()).isEqualByComparingTo("90.0");
        assertThat(progress.totals().targetElevationM()).isEqualTo(2200);

        assertThat(progress.weeks()).hasSize(2);
        WeekProgress row1 = progress.weeks().get(0);
        assertThat(row1.current()).isFalse();
        assertThat(row1.actualVolumeKm()).isEqualByComparingTo("10.50");
        assertThat(row1.actualDurationMin()).isEqualTo(115);
        assertThat(row1.sessionsPlanned()).isEqualTo(3);
        assertThat(row1.sessionsDone()).isEqualTo(2);
        assertThat(row1.sessionsSkipped()).isEqualTo(1);
        WeekProgress row2 = progress.weeks().get(1);
        assertThat(row2.current()).isTrue();
        assertThat(row2.actualVolumeKm()).isEqualByComparingTo("12.00");
        assertThat(row2.sessionsPlanned()).isEqualTo(2);
        assertThat(row2.sessionsDone()).isEqualTo(1);
    }

    @Test
    void getProgress_emptyPlanYieldsZeroTotals() {
        TrainingPlan plan = withId(new TrainingPlan(OWNER, "Plan vide", null,
                LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 30)));
        when(planService.getOwnedPlan(OWNER, plan.getId())).thenReturn(plan);
        when(weekRepository.findByPlanIdOrderByWeekNumber(plan.getId())).thenReturn(List.of());
        when(sessionRepository.findByWeekPlanId(plan.getId())).thenReturn(List.of());
        when(objectiveRepository.findByPlanId(plan.getId())).thenReturn(List.of());

        PlanProgressResponse progress = service().getProgress(OWNER, plan.getId(), TODAY);

        assertThat(progress.mainObjective()).isNull();
        assertThat(progress.currentWeekNumber()).isNull();
        assertThat(progress.totals().sessionsPlanned()).isZero();
        assertThat(progress.totals().actualVolumeKm()).isEqualByComparingTo("0");
        // no objective date → countdown to the plan's end
        assertThat(progress.daysToObjective()).isEqualTo(52);
    }
}
