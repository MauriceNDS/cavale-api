package com.cavale.training.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.cavale.common.exception.ResourceNotFoundException;
import com.cavale.training.domain.Discipline;
import com.cavale.training.domain.Objective;
import com.cavale.training.domain.ObjectiveRole;
import com.cavale.training.domain.PlanWeek;
import com.cavale.training.domain.PlannedSession;
import com.cavale.training.domain.SessionStatus;
import com.cavale.training.domain.TrainingPlan;
import com.cavale.training.domain.WeekType;
import com.cavale.training.domain.ObjectiveIntensity;
import com.cavale.training.domain.ObjectiveKind;
import com.cavale.training.domain.ObjectiveType;
import com.cavale.training.dto.CreateObjectiveRequest;
import com.cavale.training.dto.CreatePlanRequest;
import com.cavale.training.domain.Activity;
import com.cavale.training.domain.ActivitySource;
import com.cavale.training.dto.UpdateSessionRequest;
import com.cavale.training.dto.ValidateSessionRequest;
import com.cavale.training.repository.ActivityRepository;
import com.cavale.training.repository.ObjectiveRepository;
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

    @Mock
    private ActivityRepository activityRepository;

    @Mock
    private ObjectiveRepository objectiveRepository;

    @Mock
    private com.cavale.gym.service.GymTemplateService gymTemplateService;

    @Mock
    private ShoeService shoeService;

    private TrainingPlanService service() {
        return new TrainingPlanService(planRepository, weekRepository, sessionRepository,
                activityRepository, objectiveRepository, gymTemplateService, shoeService);
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
    void createPlan_createsMainObjectiveFromGoal() {
        when(planRepository.save(any(TrainingPlan.class))).thenAnswer(inv -> inv.getArgument(0));

        service().createPlan(OWNER, new CreatePlanRequest(
                "SaintéLyon 2026", "SaintéLyon 80 km", LocalDate.of(2026, 7, 6), LocalDate.of(2026, 11, 29)));

        ArgumentCaptor<Objective> captor = ArgumentCaptor.forClass(Objective.class);
        verify(objectiveRepository).save(captor.capture());
        Objective main = captor.getValue();
        assertThat(main.getRole()).isEqualTo(ObjectiveRole.MAIN);
        assertThat(main.getName()).isEqualTo("SaintéLyon 80 km");
        assertThat(main.getDate()).isEqualTo(LocalDate.of(2026, 11, 29));
    }

    @Test
    void createPlan_usesProvidedObjectiveDetails() {
        when(planRepository.save(any(TrainingPlan.class))).thenAnswer(inv -> inv.getArgument(0));

        service().createPlan(OWNER, new CreatePlanRequest(
                "Saison 2027", null, LocalDate.of(2027, 3, 1), LocalDate.of(2027, 9, 5),
                new CreateObjectiveRequest(ObjectiveType.RACE, ObjectiveKind.TRAIL,
                        ObjectiveIntensity.PERFORMANCE, "  UTMB 2027 ", LocalDate.of(2027, 8, 27),
                        new BigDecimal("171.5"), 10000, 46 * 60, "  Chamonix ", "première 100 miles")));

        ArgumentCaptor<Objective> captor = ArgumentCaptor.forClass(Objective.class);
        verify(objectiveRepository).save(captor.capture());
        Objective main = captor.getValue();
        assertThat(main.getRole()).isEqualTo(ObjectiveRole.MAIN);
        assertThat(main.getName()).isEqualTo("UTMB 2027");
        assertThat(main.getDate()).isEqualTo(LocalDate.of(2027, 8, 27));
        assertThat(main.getKind()).isEqualTo(ObjectiveKind.TRAIL);
        assertThat(main.getIntensity()).isEqualTo(ObjectiveIntensity.PERFORMANCE);
        assertThat(main.getDistanceKm()).isEqualByComparingTo("171.5");
        assertThat(main.getElevationGainM()).isEqualTo(10000);
        assertThat(main.getTargetTimeMin()).isEqualTo(46 * 60);
        assertThat(main.getLocation()).isEqualTo("Chamonix");
        assertThat(main.getNotes()).isEqualTo("première 100 miles");
    }

    @Test
    void createPlan_undatedObjectiveDefaultsToSeasonEnd() {
        when(planRepository.save(any(TrainingPlan.class))).thenAnswer(inv -> inv.getArgument(0));

        service().createPlan(OWNER, new CreatePlanRequest(
                "Reprise", null, LocalDate.of(2027, 1, 4), LocalDate.of(2027, 3, 28),
                new CreateObjectiveRequest(ObjectiveType.FITNESS, null, null,
                        "Retrouver la forme", null, null, null, null, null, null)));

        ArgumentCaptor<Objective> captor = ArgumentCaptor.forClass(Objective.class);
        verify(objectiveRepository).save(captor.capture());
        assertThat(captor.getValue().getDate()).isEqualTo(LocalDate.of(2027, 3, 28));
    }

    @Test
    void createPlan_futureStartMakesDraft() {
        when(planRepository.save(any(TrainingPlan.class))).thenAnswer(inv -> inv.getArgument(0));

        TrainingPlan plan = service().createPlan(OWNER, new CreatePlanRequest(
                "Saison 2027", "UTMB", LocalDate.now().plusDays(30), LocalDate.now().plusDays(200)));

        assertThat(plan.getStatus().name()).isEqualTo("DRAFT");
    }

    @Test
    void createPlan_mainObjectiveFallsBackToPlanName() {
        when(planRepository.save(any(TrainingPlan.class))).thenAnswer(inv -> inv.getArgument(0));

        service().createPlan(OWNER, new CreatePlanRequest(
                "Reprise hiver", "  ", LocalDate.of(2027, 1, 4), LocalDate.of(2027, 3, 28)));

        ArgumentCaptor<Objective> captor = ArgumentCaptor.forClass(Objective.class);
        verify(objectiveRepository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Reprise hiver");
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
                new UpdateSessionRequest(LocalDate.of(2026, 10, 11), null, null, null, null, null));

        assertThat(session.getDate()).isEqualTo(LocalDate.of(2026, 10, 11));
        assertThat(session.getStatus()).isEqualTo(SessionStatus.MOVED);
    }

    @Test
    void updateSession_statusOnlyValidation() {
        PlannedSession session = sessionOwnedBy(OWNER);
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));

        service().updateSession(OWNER, session.getId(),
                new UpdateSessionRequest(null, null, SessionStatus.DONE, null, null, null));

        assertThat(session.getStatus()).isEqualTo(SessionStatus.DONE);
        assertThat(session.getDate()).isEqualTo(LocalDate.of(2026, 10, 10));
    }

    @Test
    void updateSession_rejectsDateOutsidePlanRange() {
        PlannedSession session = sessionOwnedBy(OWNER);
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service().updateSession(OWNER, session.getId(),
                new UpdateSessionRequest(LocalDate.of(2027, 1, 1), null, null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateSession_hidesForeignSessionAs404() {
        PlannedSession session = sessionOwnedBy(OWNER);
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service().updateSession(STRANGER, session.getId(),
                new UpdateSessionRequest(null, null, SessionStatus.DONE, null, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void validateSession_createsManualActivityAndMarksDone() {
        PlannedSession session = sessionOwnedBy(OWNER);
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(activityRepository.findBySessionId(session.getId())).thenReturn(Optional.empty());
        when(activityRepository.save(any(Activity.class))).thenAnswer(inv -> inv.getArgument(0));

        Activity activity = service().validateSession(OWNER, session.getId(),
                new ValidateSessionRequest(245, new java.math.BigDecimal("38.50"), 1520, 151, null, true, null, "Bonne SL"));

        assertThat(activity.getSource()).isEqualTo(ActivitySource.MANUAL);
        assertThat(activity.getDurationMin()).isEqualTo(245);
        assertThat(activity.getDistanceKm()).isEqualByComparingTo("38.50");
        assertThat(session.getStatus()).isEqualTo(SessionStatus.DONE);
    }

    @Test
    void validateSession_acceptsCrossTrainingAndTagsItAsBike() {
        TrainingPlan plan = planOwnedBy(OWNER);
        PlanWeek week = new PlanWeek(plan, 3, LocalDate.of(2026, 8, 3), null,
                WeekType.BUILD, null, null, null, null);
        PlannedSession bike = new PlannedSession(week, OWNER, LocalDate.of(2026, 8, 3), 0,
                Discipline.CROSS, "Vélo home-trainer", null, null, 60, null, null, null);
        ReflectionTestUtils.setField(bike, "id", UUID.randomUUID());
        when(sessionRepository.findById(bike.getId())).thenReturn(Optional.of(bike));
        when(activityRepository.findBySessionId(bike.getId())).thenReturn(Optional.empty());
        when(activityRepository.save(any(Activity.class))).thenAnswer(inv -> inv.getArgument(0));

        Activity activity = service().validateSession(OWNER, bike.getId(),
                new ValidateSessionRequest(60, new java.math.BigDecimal("25.00"), null, null, null, null, null, null));

        assertThat(activity.getDiscipline()).isEqualTo(Discipline.CROSS);
        assertThat(activity.isRun()).isFalse();
        assertThat(activity.getDurationMin()).isEqualTo(60);
        assertThat(bike.getStatus()).isEqualTo(SessionStatus.DONE);
    }

    @Test
    void validateSession_rejectsNonRunningSession() {
        TrainingPlan plan = planOwnedBy(OWNER);
        PlanWeek week = new PlanWeek(plan, 14, LocalDate.of(2026, 10, 5), null,
                WeekType.SHOCK, null, null, null, null);
        PlannedSession gym = new PlannedSession(week, OWNER, LocalDate.of(2026, 10, 5), 0,
                Discipline.GYM, "FM-A", null, null, 55, null, null, null);
        ReflectionTestUtils.setField(gym, "id", UUID.randomUUID());
        when(sessionRepository.findById(gym.getId())).thenReturn(Optional.of(gym));

        assertThatThrownBy(() -> service().validateSession(OWNER, gym.getId(),
                new ValidateSessionRequest(55, new java.math.BigDecimal("1"), null, null, null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateSession_resetToPlannedDeletesManualActivity() {
        PlannedSession session = sessionOwnedBy(OWNER);
        session.updateStatus(SessionStatus.DONE);
        Activity activity = new Activity(session, ActivitySource.MANUAL, session.getDate(),
                240, new java.math.BigDecimal("38.0"), null, null, null);
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(activityRepository.findBySessionId(session.getId())).thenReturn(Optional.of(activity));

        service().updateSession(OWNER, session.getId(),
                new UpdateSessionRequest(null, null, SessionStatus.PLANNED, null, null, null));

        verify(activityRepository).delete(activity);
        assertThat(session.getStatus()).isEqualTo(SessionStatus.PLANNED);
    }
}
