package com.cavale.training.service;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.cavale.common.exception.ResourceNotFoundException;
import com.cavale.training.domain.Discipline;
import com.cavale.training.domain.PlanWeek;
import com.cavale.training.domain.PlannedSession;
import com.cavale.training.domain.SessionStatus;
import com.cavale.training.domain.TrainingPlan;
import com.cavale.training.domain.WeekType;
import com.cavale.training.dto.CreatePlanRequest;
import com.cavale.training.dto.UpdateSessionRequest;
import com.cavale.training.repository.PlanWeekRepository;
import com.cavale.training.repository.PlannedSessionRepository;
import com.cavale.training.repository.TrainingPlanRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrainingPlanServiceTest {

    @Mock
    private TrainingPlanRepository planRepository;

    @Mock
    private PlanWeekRepository weekRepository;

    @Mock
    private PlannedSessionRepository sessionRepository;

    private TrainingPlanService service() {
        return new TrainingPlanService(planRepository, weekRepository, sessionRepository);
    }

    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID STRANGER = UUID.randomUUID();

    private static TrainingPlan planOwnedBy(UUID userId) {
        TrainingPlan plan = new TrainingPlan(userId, "SaintéLyon 2026", "sub-8h30",
                LocalDate.of(2026, 7, 6), LocalDate.of(2026, 11, 29));
        ReflectionTestUtils.setField(plan, "id", UUID.randomUUID());
        return plan;
    }

    @Test
    void createPlan_savesActivePlan() {
        when(planRepository.save(any(TrainingPlan.class))).thenAnswer(inv -> inv.getArgument(0));

        TrainingPlan plan = service().createPlan(OWNER, new CreatePlanRequest(
                "  SaintéLyon 2026 ", "sub-8h30", LocalDate.of(2026, 7, 6), LocalDate.of(2026, 11, 29)));

        assertThat(plan.getName()).isEqualTo("SaintéLyon 2026");
        assertThat(plan.getUserId()).isEqualTo(OWNER);
        assertThat(plan.getStatus().name()).isEqualTo("ACTIVE");
    }

    @Test
    void createPlan_rejectsEndBeforeStart() {
        assertThatThrownBy(() -> service().createPlan(OWNER, new CreatePlanRequest(
                "Plan", null, LocalDate.of(2026, 11, 29), LocalDate.of(2026, 7, 6))))
                .isInstanceOf(IllegalArgumentException.class);

        verify(planRepository, never()).save(any());
    }

    @Test
    void getOwnedPlan_returnsOwnPlan() {
        TrainingPlan plan = planOwnedBy(OWNER);
        when(planRepository.findById(plan.getId())).thenReturn(Optional.of(plan));

        assertThat(service().getOwnedPlan(OWNER, plan.getId())).isSameAs(plan);
    }

    @Test
    void getOwnedPlan_hidesForeignPlanAs404() {
        TrainingPlan plan = planOwnedBy(OWNER);
        when(planRepository.findById(plan.getId())).thenReturn(Optional.of(plan));

        assertThatThrownBy(() -> service().getOwnedPlan(STRANGER, plan.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getCalendar_rejectsInvertedRange() {
        assertThatThrownBy(() -> service().getCalendar(OWNER,
                LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private PlannedSession sessionOwnedBy(UUID userId) {
        TrainingPlan plan = planOwnedBy(userId);
        PlanWeek week = new PlanWeek(plan, 14, LocalDate.of(2026, 10, 5), null,
                WeekType.SHOCK, null, null, null, null);
        PlannedSession session = new PlannedSession(week, userId, LocalDate.of(2026, 10, 10), 0,
                Discipline.RUN, "SL 4h nocturne", null, "EF", 240, 1500, 4, 5);
        ReflectionTestUtils.setField(session, "id", UUID.randomUUID());
        return session;
    }

    @Test
    void updateSession_moveMarksPlannedSessionAsMoved() {
        PlannedSession session = sessionOwnedBy(OWNER);
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));

        service().updateSession(OWNER, session.getId(),
                new UpdateSessionRequest(LocalDate.of(2026, 10, 11), null, null));

        assertThat(session.getDate()).isEqualTo(LocalDate.of(2026, 10, 11));
        assertThat(session.getStatus()).isEqualTo(SessionStatus.MOVED);
    }

    @Test
    void updateSession_statusOnlyValidation() {
        PlannedSession session = sessionOwnedBy(OWNER);
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));

        service().updateSession(OWNER, session.getId(),
                new UpdateSessionRequest(null, null, SessionStatus.DONE));

        assertThat(session.getStatus()).isEqualTo(SessionStatus.DONE);
        assertThat(session.getDate()).isEqualTo(LocalDate.of(2026, 10, 10));
    }

    @Test
    void updateSession_rejectsDateOutsidePlanRange() {
        PlannedSession session = sessionOwnedBy(OWNER);
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service().updateSession(OWNER, session.getId(),
                new UpdateSessionRequest(LocalDate.of(2027, 1, 1), null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateSession_hidesForeignSessionAs404() {
        PlannedSession session = sessionOwnedBy(OWNER);
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service().updateSession(STRANGER, session.getId(),
                new UpdateSessionRequest(null, null, SessionStatus.DONE)))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
