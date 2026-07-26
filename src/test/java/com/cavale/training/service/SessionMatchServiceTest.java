package com.cavale.training.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.cavale.training.domain.Activity;
import com.cavale.training.domain.Discipline;
import com.cavale.training.domain.PlanWeek;
import com.cavale.training.domain.PlannedSession;
import com.cavale.training.domain.SessionStatus;
import com.cavale.training.domain.TrainingPlan;
import com.cavale.training.domain.WeekType;
import com.cavale.training.repository.ActivityRepository;
import com.cavale.training.repository.PlannedSessionRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionMatchServiceTest {

    private static final UUID USER = UUID.randomUUID();
    private static final LocalDate DATE = LocalDate.of(2026, 7, 10);

    @Mock
    private ActivityRepository activityRepository;

    @Mock
    private PlannedSessionRepository sessionRepository;

    private SessionMatchService service() {
        return new SessionMatchService(activityRepository, sessionRepository);
    }

    private static PlannedSession runSession(LocalDate date, Integer durationMin) {
        TrainingPlan plan = new TrainingPlan(USER, "Plan", null, date.minusDays(30), date.plusDays(60));
        PlanWeek week = new PlanWeek(plan, 1, date, null, WeekType.BUILD, null, null, null, null);
        PlannedSession session = new PlannedSession(week, USER, date, 0, Discipline.RUN,
                "EF", null, "EF", durationMin, null, null, null);
        ReflectionTestUtils.setField(session, "id", UUID.randomUUID());
        return session;
    }

    private static Activity run(LocalDate date, int durationMin, long externalId) {
        return Activity.stravaHistory(USER, date, durationMin,
                new BigDecimal("10.00"), 150, 145, "Sortie " + externalId, externalId);
    }

    private void candidates(Activity... activities) {
        when(activityRepository.findByUserIdAndSessionIsNullAndDateBetween(eq(USER), any(), any()))
                .thenReturn(List.of(activities));
    }

    private void noOtherSessions() {
        when(sessionRepository.findByUserIdAndDateBetweenOrderByDateAscOrderInDayAsc(eq(USER), any(), any()))
                .thenReturn(List.of());
    }

    @Test
    void sameDayRun_isProposed_closestDurationWins() {
        PlannedSession session = runSession(DATE, 60);
        Activity close = run(DATE, 62, 1L);
        Activity far = run(DATE, 130, 2L);
        candidates(far, close);
        noOtherSessions();

        Optional<Activity> proposal = service().proposeFor(session);

        assertThat(proposal).contains(close);
    }

    @Test
    void sameDayRun_isProposedEvenWithLargeDurationGap() {
        PlannedSession session = runSession(DATE, 60);
        Activity shortened = run(DATE, 25, 1L); // cut short, still that session
        candidates(shortened);
        noOtherSessions();

        assertThat(service().proposeFor(session)).contains(shortened);
    }

    @Test
    void offDayRun_proposedOnlyWhenDurationIsClose() {
        PlannedSession session = runSession(DATE, 60);
        Activity dayLateClose = run(DATE.plusDays(1), 65, 1L);
        candidates(dayLateClose);
        noOtherSessions();
        assertThat(service().proposeFor(session)).contains(dayLateClose);

        Activity dayLateFar = run(DATE.plusDays(1), 120, 2L); // 100% off — someone else's run
        candidates(dayLateFar);
        assertThat(service().proposeFor(session)).isEmpty();
    }

    @Test
    void offDayRun_notProposedWithoutPlannedDuration() {
        PlannedSession session = runSession(DATE, null);
        candidates(run(DATE.plusDays(1), 65, 1L));
        noOtherSessions();

        assertThat(service().proposeFor(session)).isEmpty();
    }

    @Test
    void offDayRun_notStolenFromItsOwnDaysSession() {
        PlannedSession session = runSession(DATE, 60);
        PlannedSession tomorrow = runSession(DATE.plusDays(1), 60);
        Activity tomorrowsRun = run(DATE.plusDays(1), 61, 1L);
        candidates(tomorrowsRun);
        when(sessionRepository.findByUserIdAndDateBetweenOrderByDateAscOrderInDayAsc(eq(USER), any(), any()))
                .thenReturn(List.of(session, tomorrow));

        assertThat(service().proposeFor(session)).isEmpty();
        // ...but tomorrow's own session gets it
        assertThat(service().proposeFor(tomorrow)).contains(tomorrowsRun);
    }

    @Test
    void sameDayBeatsOffDayEvenWithWorseDuration() {
        PlannedSession session = runSession(DATE, 60);
        Activity sameDay = run(DATE, 80, 1L);
        Activity offDayPerfect = run(DATE.plusDays(1), 60, 2L);
        candidates(sameDay, offDayPerfect);
        noOtherSessions();

        assertThat(service().proposeFor(session)).contains(sameDay);
    }

    @Test
    void nonRunOrNonPlannedSessions_getNoProposal() {
        PlannedSession gym = new PlannedSession(
                runSession(DATE, 60).getWeek(), USER, DATE, 0, Discipline.GYM,
                "Renfo", null, null, 45, null, null, null);
        assertThat(service().proposeFor(gym)).isEmpty();

        PlannedSession done = runSession(DATE, 60);
        done.updateStatus(SessionStatus.DONE);
        assertThat(service().proposeFor(done)).isEmpty();
    }

    @Test
    void hike_neverProposedForRunSession_andConversely() {
        PlannedSession session = runSession(DATE, 240);
        Activity hike = run(DATE, 240, 1L);
        hike.markDiscipline(Discipline.HIKE);
        candidates(hike);
        noOtherSessions();

        assertThat(service().proposeFor(session)).isEmpty();

        PlannedSession trek = new PlannedSession(session.getWeek(), USER, DATE, 0,
                Discipline.HIKE, "Trek Chamonix", null, null, 240, 1200, null, null);
        ReflectionTestUtils.setField(trek, "id", UUID.randomUUID());
        assertThat(service().proposeFor(trek)).contains(hike);
    }

    @Test
    void manualActivities_areNeverProposed() {
        PlannedSession session = runSession(DATE, 60);
        Activity manual = run(DATE, 60, 1L);
        ReflectionTestUtils.setField(manual, "externalId", null);
        candidates(manual);
        noOtherSessions();

        assertThat(service().proposeFor(session)).isEmpty();
    }
}
