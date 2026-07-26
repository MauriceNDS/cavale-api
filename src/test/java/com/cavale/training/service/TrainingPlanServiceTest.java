package com.cavale.training.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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
    void createPlan_storesPreferences() {
        when(planRepository.save(any(TrainingPlan.class))).thenAnswer(inv -> inv.getArgument(0));

        TrainingPlan plan = service().createPlan(OWNER, new CreatePlanRequest(
                "Saison", null, LocalDate.of(2027, 1, 4), LocalDate.of(2027, 3, 28),
                4, 2, com.cavale.training.domain.PlanFocus.SPEED, null));

        assertThat(plan.getRunsPerWeek()).isEqualTo(4);
        assertThat(plan.getGymPerWeek()).isEqualTo(2);
        assertThat(plan.getFocus()).isEqualTo(com.cavale.training.domain.PlanFocus.SPEED);
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
                new UpdateSessionRequest(LocalDate.of(2026, 10, 11), null, null, null, null, null, null, null, null, null, null, null, null));

        assertThat(session.getDate()).isEqualTo(LocalDate.of(2026, 10, 11));
        assertThat(session.getStatus()).isEqualTo(SessionStatus.MOVED);
    }

    @Test
    void updateSession_crossWeekMoveReassignsWeek() {
        TrainingPlan plan = planOwnedBy(OWNER);
        PlanWeek week1 = new PlanWeek(plan, 1, LocalDate.of(2026, 10, 5), null,
                WeekType.BUILD, null, null, null, null);
        PlanWeek week2 = new PlanWeek(plan, 2, LocalDate.of(2026, 10, 12), null,
                WeekType.BUILD, null, null, null, null);
        ReflectionTestUtils.setField(week1, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(week2, "id", UUID.randomUUID());
        PlannedSession session = new PlannedSession(week1, OWNER, LocalDate.of(2026, 10, 8), 0,
                Discipline.RUN, "EF", null, "EF", 60, null, 3, 4);
        ReflectionTestUtils.setField(session, "id", UUID.randomUUID());
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(weekRepository.findByPlanIdOrderByWeekNumber(plan.getId()))
                .thenReturn(List.of(week1, week2));

        // move into week 2's 7-day span
        service().updateSession(OWNER, session.getId(),
                new UpdateSessionRequest(LocalDate.of(2026, 10, 15), null, null, null, null, null, null, null, null, null, null, null, null));

        assertThat(session.getWeek()).isEqualTo(week2);
    }

    @Test
    void updateSession_statusOnlyValidation() {
        PlannedSession session = sessionOwnedBy(OWNER);
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));

        service().updateSession(OWNER, session.getId(),
                new UpdateSessionRequest(null, null, null, null, null, null, null, null, null, SessionStatus.DONE, null, null, null));

        assertThat(session.getStatus()).isEqualTo(SessionStatus.DONE);
        assertThat(session.getDate()).isEqualTo(LocalDate.of(2026, 10, 10));
    }

    @Test
    void updateSession_rejectsDateOutsidePlanRange() {
        PlannedSession session = sessionOwnedBy(OWNER);
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service().updateSession(OWNER, session.getId(),
                new UpdateSessionRequest(LocalDate.of(2027, 1, 1), null, null, null, null, null, null, null, null, null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateSession_rejectsMovingDoneSession() {
        PlannedSession session = sessionOwnedBy(OWNER);
        session.updateStatus(SessionStatus.DONE);
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service().updateSession(OWNER, session.getId(),
                new UpdateSessionRequest(LocalDate.of(2026, 10, 11), null, null, null, null, null, null, null, null, null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be moved");
        assertThat(session.getDate()).isEqualTo(LocalDate.of(2026, 10, 10));
    }

    @Test
    void updateSession_rejectsMovingSkippedSession() {
        PlannedSession session = sessionOwnedBy(OWNER);
        session.updateStatus(SessionStatus.SKIPPED);
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service().updateSession(OWNER, session.getId(),
                new UpdateSessionRequest(LocalDate.of(2026, 10, 11), null, null, null, null, null, null, null, null, null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateSession_sameDateOnDoneSessionIsNotAMove() {
        PlannedSession session = sessionOwnedBy(OWNER);
        session.updateStatus(SessionStatus.DONE);
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));

        // idempotent re-send of the current date must stay accepted (MCP edits
        // often echo the whole session back)
        service().updateSession(OWNER, session.getId(),
                new UpdateSessionRequest(LocalDate.of(2026, 10, 10), null, null, null, null, null, null, null, null, null, null, null, null));

        assertThat(session.getStatus()).isEqualTo(SessionStatus.DONE);
    }

    @Test
    void updateSession_hidesForeignSessionAs404() {
        PlannedSession session = sessionOwnedBy(OWNER);
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service().updateSession(STRANGER, session.getId(),
                new UpdateSessionRequest(null, null, null, null, null, null, null, null, null, SessionStatus.DONE, null, null, null)))
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
    void updateSession_revisesContentAndReparsesWorkout() {
        PlannedSession session = sessionOwnedBy(OWNER);
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));

        service().updateSession(OWNER, session.getId(),
                new UpdateSessionRequest(null, null, "  Seuil long ",
                        "20′ EF + 3×10′ Seuil 60 (récup 3′) + 10′ EF", "Seuil 60",
                        75, 300, 6, 7, null, null, null, null));

        assertThat(session.getTitle()).isEqualTo("Seuil long");
        assertThat(session.getDetail()).isEqualTo("20′ EF + 3×10′ Seuil 60 (récup 3′) + 10′ EF");
        assertThat(session.getZone()).isEqualTo("Seuil 60");
        assertThat(session.getDurationMin()).isEqualTo(75);
        assertThat(session.getElevationM()).isEqualTo(300);
        assertThat(session.getRpeMin()).isEqualTo(6);
        assertThat(session.getRpeMax()).isEqualTo(7);
        assertThat(session.getWorkoutJson()).isNotNull();
        assertThat(session.getDate()).isEqualTo(LocalDate.of(2026, 10, 10));
    }

    @Test
    void updateSession_partialContentKeepsOtherFields() {
        PlannedSession session = sessionOwnedBy(OWNER);
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));

        service().updateSession(OWNER, session.getId(),
                new UpdateSessionRequest(null, null, null, null, null,
                        270, null, null, null, null, null, null, null));

        assertThat(session.getDurationMin()).isEqualTo(270);
        assertThat(session.getTitle()).isEqualTo("SL 4h nocturne");
        assertThat(session.getZone()).isEqualTo("EF");
        assertThat(session.getElevationM()).isEqualTo(1500);
    }

    @Test
    void updateSession_rejectsBlankTitle() {
        PlannedSession session = sessionOwnedBy(OWNER);
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service().updateSession(OWNER, session.getId(),
                new UpdateSessionRequest(null, null, "  ", null, null,
                        null, null, null, null, null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private PlanWeek weekOwnedBy(UUID userId) {
        PlanWeek week = new PlanWeek(planOwnedBy(userId), 14, LocalDate.of(2026, 10, 5), "Base",
                WeekType.BUILD, new BigDecimal("55.0"), 1200, null, "Volume");
        ReflectionTestUtils.setField(week, "id", UUID.randomUUID());
        return week;
    }

    @Test
    void updateWeek_revisesPlanningAndFocus() {
        PlanWeek week = weekOwnedBy(OWNER);
        when(weekRepository.findById(week.getId())).thenReturn(Optional.of(week));

        service().updateWeek(OWNER, week.getId(), new com.cavale.training.dto.UpdateWeekRequest(
                " Spécifique ", WeekType.SHOCK, new BigDecimal("70.0"), 2000, 600, "Bloc choc"));

        assertThat(week.getPhase()).isEqualTo("Spécifique");
        assertThat(week.getWeekType()).isEqualTo(WeekType.SHOCK);
        assertThat(week.getTargetVolumeKm()).isEqualByComparingTo("70.0");
        assertThat(week.getTargetElevationM()).isEqualTo(2000);
        assertThat(week.getTargetLoadUa()).isEqualTo(600);
        assertThat(week.getFocus()).isEqualTo("Bloc choc");
    }

    @Test
    void updateWeek_partialKeepsUnsentFields() {
        PlanWeek week = weekOwnedBy(OWNER);
        when(weekRepository.findById(week.getId())).thenReturn(Optional.of(week));

        service().updateWeek(OWNER, week.getId(), new com.cavale.training.dto.UpdateWeekRequest(
                null, null, new BigDecimal("60.0"), null, null, null));

        assertThat(week.getTargetVolumeKm()).isEqualByComparingTo("60.0");
        assertThat(week.getWeekType()).isEqualTo(WeekType.BUILD);
        assertThat(week.getPhase()).isEqualTo("Base");
        assertThat(week.getFocus()).isEqualTo("Volume");
    }

    @Test
    void deleteSession_deletesManualActivityWithIt() {
        PlannedSession session = sessionOwnedBy(OWNER);
        Activity activity = new Activity(session, ActivitySource.MANUAL, session.getDate(),
                240, new java.math.BigDecimal("38.0"), null, null, null);
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(activityRepository.findBySessionId(session.getId())).thenReturn(Optional.of(activity));

        service().deleteSession(OWNER, session.getId());

        verify(activityRepository).delete(activity);
        verify(sessionRepository).delete(session);
    }

    @Test
    void deleteSession_detachesStravaActivity() {
        PlannedSession session = sessionOwnedBy(OWNER);
        Activity activity = new Activity(session, ActivitySource.STRAVA, session.getDate(),
                240, new java.math.BigDecimal("38.0"), null, null, null);
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(activityRepository.findBySessionId(session.getId())).thenReturn(Optional.of(activity));

        service().deleteSession(OWNER, session.getId());

        assertThat(activity.getSession()).isNull();
        verify(activityRepository, never()).delete(any());
        verify(sessionRepository).delete(session);
    }

    @Test
    void deleteSession_hidesForeignSessionAs404() {
        PlannedSession session = sessionOwnedBy(OWNER);
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service().deleteSession(STRANGER, session.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(sessionRepository, never()).delete(any(PlannedSession.class));
    }

    @Test
    void deleteWeek_releasesActivitiesThenDeletes() {
        PlanWeek week = weekOwnedBy(OWNER);
        PlannedSession session = new PlannedSession(week, OWNER, LocalDate.of(2026, 10, 10), 0,
                Discipline.RUN, "SL", null, "EF", 240, null, null, null);
        ReflectionTestUtils.setField(session, "id", UUID.randomUUID());
        Activity strava = new Activity(session, ActivitySource.STRAVA, session.getDate(),
                240, new java.math.BigDecimal("38.0"), null, null, null);
        when(weekRepository.findById(week.getId())).thenReturn(Optional.of(week));
        when(sessionRepository.findByWeekIdOrderByDateAscOrderInDayAsc(week.getId()))
                .thenReturn(List.of(session));
        when(activityRepository.findBySessionId(session.getId())).thenReturn(Optional.of(strava));

        service().deleteWeek(OWNER, week.getId());

        assertThat(strava.getSession()).isNull();
        verify(weekRepository).delete(week);
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
                new UpdateSessionRequest(null, null, null, null, null, null, null, null, null, SessionStatus.PLANNED, null, null, null));

        verify(activityRepository).delete(activity);
        assertThat(session.getStatus()).isEqualTo(SessionStatus.PLANNED);
    }
}
