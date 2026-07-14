package com.cavale.athlete.service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.cavale.athlete.dto.AthleteContextResponse;
import com.cavale.training.domain.Activity;
import com.cavale.training.domain.Discipline;
import com.cavale.training.domain.Objective;
import com.cavale.training.domain.ObjectiveRole;
import com.cavale.training.domain.ObjectiveType;
import com.cavale.training.domain.PerceivedEffort;
import com.cavale.training.domain.PlanWeek;
import com.cavale.training.domain.PlannedSession;
import com.cavale.training.domain.SessionStatus;
import com.cavale.training.domain.TrainingPlan;
import com.cavale.training.domain.WeekType;
import com.cavale.training.repository.ActivityBestEffortRepository;
import com.cavale.training.repository.ActivityRepository;
import com.cavale.training.repository.ObjectiveRepository;
import com.cavale.training.repository.PlanWeekRepository;
import com.cavale.training.repository.PlannedSessionRepository;
import com.cavale.training.repository.TrainingPlanRepository;
import com.cavale.user.domain.AthleteStatus;
import com.cavale.user.domain.User;
import com.cavale.user.service.UserService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AthleteContextServiceTest {

    private static final UUID USER = UUID.randomUUID();
    /** A Monday, so week bucketing is explicit. */
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 13);

    @Mock
    private UserService userService;

    @Mock
    private TrainingPlanRepository planRepository;

    @Mock
    private PlanWeekRepository weekRepository;

    @Mock
    private PlannedSessionRepository sessionRepository;

    @Mock
    private ActivityRepository activityRepository;

    @Mock
    private ActivityBestEffortRepository bestEffortRepository;

    @Mock
    private ObjectiveRepository objectiveRepository;

    @Mock
    private com.cavale.gym.service.GymStatsService gymStatsService;

    @Mock
    private com.cavale.gym.repository.WorkoutLogRepository workoutLogRepository;

    @Mock
    private RunningStatsService runningStatsService;

    private AthleteContextService service() {
        org.mockito.Mockito.lenient().when(gymStatsService.getStats(org.mockito.ArgumentMatchers.eq(USER),
                        org.mockito.ArgumentMatchers.any(LocalDate.class)))
                .thenReturn(new com.cavale.gym.dto.GymStatsResponse(
                        java.util.List.of(), java.util.List.of(), java.util.List.of(),
                        java.util.List.of(), java.util.List.of()));
        org.mockito.Mockito.lenient().when(runningStatsService.getStats(
                        org.mockito.ArgumentMatchers.eq(USER),
                        org.mockito.ArgumentMatchers.any(LocalDate.class)))
                .thenReturn(new com.cavale.athlete.dto.RunningStatsResponse(
                        java.util.List.of(), java.util.List.of(),
                        new com.cavale.athlete.dto.RunningStatsResponse.Acwr(0, 0, 0,
                                com.cavale.athlete.dto.RunningStatsResponse.AcwrZone.UNDER),
                        java.util.List.of(), java.util.List.of(), java.util.List.of(),
                        java.util.List.of(), java.util.List.of()));
        return new AthleteContextService(userService, planRepository, weekRepository,
                sessionRepository, activityRepository, bestEffortRepository, objectiveRepository,
                gymStatsService, workoutLogRepository, runningStatsService);
    }

    private static User user() {
        User user = new User("a@b.c", "hash", "Arsène");
        user.updateStatus(AthleteStatus.RECOVERING, "TFL genou droit", TODAY.minusDays(10));
        return user;
    }

    @Test
    void context_reportsStatusWithDaysSince() {
        when(userService.getById(USER)).thenReturn(user());
        emptyWorld();

        AthleteContextResponse context = service().getContext(USER, TODAY);

        assertThat(context.status().status()).isEqualTo(AthleteStatus.RECOVERING);
        assertThat(context.status().note()).isEqualTo("TFL genou droit");
        assertThat(context.status().daysSince()).isEqualTo(10);
        assertThat(context.season()).isNull();
        assertThat(context.lastRace()).isNull();
        assertThat(context.recentWeeks()).hasSize(6);
    }

    @Test
    void context_locatesTheAthleteInsideTheCurrentSeason() {
        when(userService.getById(USER)).thenReturn(user());
        emptyWorld();

        TrainingPlan plan = new TrainingPlan(USER, "SaintéLyon 2026", "SaintéLyon 80 km",
                TODAY.minusWeeks(1), TODAY.plusWeeks(18));
        when(planRepository.findByUserIdOrderByStartDateDesc(USER)).thenReturn(List.of(plan));
        PlanWeek week1 = new PlanWeek(plan, 1, TODAY.minusWeeks(1), "Base", WeekType.BUILD,
                null, null, null, null);
        PlanWeek week2 = new PlanWeek(plan, 2, TODAY, "Base", WeekType.BUILD, null, null, null, null);
        when(weekRepository.findByPlanIdOrderByWeekNumber(any())).thenReturn(List.of(week1, week2));
        when(objectiveRepository.findByPlanIdAndRole(any(), eq(ObjectiveRole.MAIN)))
                .thenReturn(Optional.of(new Objective(plan, ObjectiveRole.MAIN, ObjectiveType.RACE,
                        "SaintéLyon 80 km", TODAY.plusWeeks(18))));

        AthleteContextResponse context = service().getContext(USER, TODAY);

        assertThat(context.season().name()).isEqualTo("SaintéLyon 2026");
        assertThat(context.season().currentWeekNumber()).isEqualTo(2);
        assertThat(context.season().totalWeeks()).isEqualTo(2);
        assertThat(context.season().currentWeekType()).isEqualTo(WeekType.BUILD);
        assertThat(context.season().mainObjective().name()).isEqualTo("SaintéLyon 80 km");
    }

    @Test
    void context_aggregatesWeeklyLoadWithPainFlagsAndAdherence() {
        when(userService.getById(USER)).thenReturn(user());
        when(objectiveRepository.findByUserId(USER)).thenReturn(List.of());
        when(planRepository.findByUserIdOrderByStartDateDesc(USER)).thenReturn(List.of());
        when(bestEffortRepository.findByUserId(USER)).thenReturn(List.of());

        LocalDate lastMonday = TODAY.with(DayOfWeek.MONDAY).minusWeeks(1);
        Activity fine = Activity.stravaHistory(USER, lastMonday, 60,
                new BigDecimal("11.00"), 200, 145, "EF", 1L);
        fine.recordFeedback(PerceivedEffort.COMME_PREVU, null, false);
        Activity painful = Activity.stravaHistory(USER, lastMonday.plusDays(2), 80,
                new BigDecimal("15.00"), 400, 150, "SL", 2L);
        painful.recordFeedback(PerceivedEffort.DIFFICILE, "genou douloureux", true);
        when(activityRepository.findByUserId(USER)).thenReturn(List.of(fine, painful));

        TrainingPlan plan = new TrainingPlan(USER, "Plan", null,
                lastMonday.minusWeeks(2), lastMonday.plusWeeks(8));
        PlanWeek week = new PlanWeek(plan, 3, lastMonday, null, WeekType.BUILD, null, null, null, null);
        PlannedSession done = new PlannedSession(week, USER, lastMonday, 0, Discipline.RUN,
                "EF 60′", null, "EF", 60, null, null, null);
        done.updateStatus(SessionStatus.DONE);
        PlannedSession skipped = new PlannedSession(week, USER, lastMonday.plusDays(4), 0,
                Discipline.RUN, "Seuil", null, "Seuil 60", 70, null, null, null);
        skipped.updateStatus(SessionStatus.SKIPPED);
        PlannedSession rest = new PlannedSession(week, USER, lastMonday.plusDays(5), 0,
                Discipline.REST, "Repos", null, null, null, null, null, null);
        when(sessionRepository.findByUserIdAndDateBetweenOrderByDateAscOrderInDayAsc(
                eq(USER), any(), any())).thenReturn(List.of(done, skipped, rest));

        AthleteContextResponse context = service().getContext(USER, TODAY);

        AthleteContextResponse.WeekLoad lastWeek = context.recentWeeks().get(4); // 6 weeks, index 4 = W-1
        assertThat(lastWeek.weekStart()).isEqualTo(lastMonday);
        assertThat(lastWeek.plannedSessions()).isEqualTo(2); // REST never counts
        assertThat(lastWeek.doneSessions()).isEqualTo(1);
        assertThat(lastWeek.skippedSessions()).isEqualTo(1);
        assertThat(lastWeek.runs()).isEqualTo(2);
        assertThat(lastWeek.distanceKm()).isEqualByComparingTo("26.00");
        assertThat(lastWeek.painFlags()).isEqualTo(1);

        assertThat(context.recentFeedback()).hasSize(2);
        assertThat(context.recentFeedback().getFirst().painFlag()).isTrue(); // newest first
        assertThat(context.recentFeedback().getFirst().comment()).isEqualTo("genou douloureux");
    }

    @Test
    void context_findsLastRaceAndUpcomingObjectives() {
        when(userService.getById(USER)).thenReturn(user());
        when(activityRepository.findByUserId(USER)).thenReturn(List.of());
        when(planRepository.findByUserIdOrderByStartDateDesc(USER)).thenReturn(List.of());
        when(bestEffortRepository.findByUserId(USER)).thenReturn(List.of());
        when(sessionRepository.findByUserIdAndDateBetweenOrderByDateAscOrderInDayAsc(
                eq(USER), any(), any())).thenReturn(List.of());

        TrainingPlan oldPlan = new TrainingPlan(USER, "Saison 2025", null,
                TODAY.minusMonths(10), TODAY.minusDays(14));
        Objective raced = new Objective(oldPlan, ObjectiveRole.MAIN, ObjectiveType.RACE,
                "SaintéLyon 2025", TODAY.minusDays(14));
        raced.recordResult(582);
        Objective nextRace = new Objective(oldPlan, ObjectiveRole.SECONDARY, ObjectiveType.RACE,
                "Trail des Cabornis", TODAY.plusDays(40));
        when(objectiveRepository.findByUserId(USER)).thenReturn(List.of(raced, nextRace));

        AthleteContextResponse context = service().getContext(USER, TODAY);

        assertThat(context.lastRace().name()).isEqualTo("SaintéLyon 2025");
        assertThat(context.lastRace().daysSince()).isEqualTo(14);
        assertThat(context.lastRace().resultTimeMin()).isEqualTo(582);
        assertThat(context.upcoming()).hasSize(1);
        assertThat(context.upcoming().getFirst().daysUntil()).isEqualTo(40);
    }

    private void emptyWorld() {
        when(activityRepository.findByUserId(USER)).thenReturn(List.of());
        when(objectiveRepository.findByUserId(USER)).thenReturn(List.of());
        when(planRepository.findByUserIdOrderByStartDateDesc(USER)).thenReturn(List.of());
        when(bestEffortRepository.findByUserId(USER)).thenReturn(List.of());
        when(sessionRepository.findByUserIdAndDateBetweenOrderByDateAscOrderInDayAsc(
                eq(USER), any(), any())).thenReturn(List.of());
    }
}
