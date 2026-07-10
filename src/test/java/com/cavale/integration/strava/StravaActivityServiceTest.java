package com.cavale.integration.strava;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.cavale.training.domain.Activity;
import com.cavale.training.domain.ActivitySource;
import com.cavale.training.domain.Discipline;
import com.cavale.training.domain.PlanWeek;
import com.cavale.training.domain.PlannedSession;
import com.cavale.training.domain.SessionStatus;
import com.cavale.training.domain.TrainingPlan;
import com.cavale.training.domain.WeekType;
import com.cavale.training.repository.ActivityRepository;
import com.cavale.training.repository.PlannedSessionRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StravaActivityServiceTest {

    private static final UUID USER = UUID.randomUUID();

    @Mock
    private StravaAuthService authService;

    @Mock
    private StravaClient stravaClient;

    @Mock
    private PlannedSessionRepository sessionRepository;

    @Mock
    private ActivityRepository activityRepository;

    private StravaActivityService service() {
        return new StravaActivityService(authService, stravaClient, sessionRepository, activityRepository);
    }

    private static StravaConnection connection() {
        return new StravaConnection(USER, 42L, "access", "refresh",
                Instant.now().plusSeconds(3600), "activity:read_all");
    }

    private static StravaDtos.ActivitySummary run(long id, LocalDate date, int minutes, String sport) {
        return new StravaDtos.ActivitySummary(id, "Sortie " + id, sport,
                LocalDateTime.of(date, LocalTime.of(8, 0)), 10500, minutes * 60, 180, 149.2);
    }

    private static PlannedSession runSession(LocalDate date) {
        TrainingPlan plan = new TrainingPlan(USER, "Plan", null, date.minusDays(10), date.plusDays(60));
        PlanWeek week = new PlanWeek(plan, 1, date, null, WeekType.BUILD, null, null, null, null);
        PlannedSession session = new PlannedSession(week, USER, date, 0, Discipline.RUN,
                "EF 60′", null, "EF", 60, null, 2, 3);
        ReflectionTestUtils.setField(session, "id", UUID.randomUUID());
        return session;
    }

    @Test
    void listRecent_excludesImportedAndNonRuns_newestFirst() {
        LocalDate today = LocalDate.now();
        when(authService.freshConnection(USER)).thenReturn(connection());
        when(stravaClient.listActivities(anyString(), any(), any())).thenReturn(List.of(
                run(1L, today.minusDays(5), 50, "Run"),
                run(2L, today.minusDays(1), 65, "TrailRun"),
                run(3L, today.minusDays(2), 90, "Ride")));
        Activity imported = mock(Activity.class);
        when(imported.getExternalId()).thenReturn(1L);
        when(activityRepository.findByExternalIdIn(anyCollection())).thenReturn(List.of(imported));

        var options = service().listRecentNotImported(USER);

        assertThat(options).extracting(StravaActivityService.StravaActivityOption::id)
                .containsExactly(2L); // ride filtered, imported filtered, newest first
        assertThat(options.getFirst().distanceKm()).isEqualByComparingTo(new BigDecimal("10.50"));
    }

    @Test
    void importToSession_attachesActivityAndValidates() {
        LocalDate date = LocalDate.now().minusDays(1);
        PlannedSession session = runSession(date);
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(activityRepository.findBySessionId(session.getId())).thenReturn(Optional.empty());
        when(activityRepository.findByExternalId(7L)).thenReturn(Optional.empty());
        when(authService.freshConnection(USER)).thenReturn(connection());
        when(stravaClient.listActivities(anyString(), any(), any()))
                .thenReturn(List.of(run(7L, date, 62, "Run")));
        when(activityRepository.save(any(Activity.class))).thenAnswer(inv -> inv.getArgument(0));

        service().importToSession(USER, session.getId(), 7L);

        assertThat(session.getStatus()).isEqualTo(SessionStatus.DONE);
        ArgumentCaptor<Activity> captor = ArgumentCaptor.forClass(Activity.class);
        org.mockito.Mockito.verify(activityRepository).save(captor.capture());
        assertThat(captor.getValue().getSource()).isEqualTo(ActivitySource.STRAVA);
        assertThat(captor.getValue().getExternalId()).isEqualTo(7L);
        assertThat(captor.getValue().getDurationMin()).isEqualTo(62);
    }

    @Test
    void importToSession_rejectsSessionsThatAlreadyHaveMeasures() {
        PlannedSession session = runSession(LocalDate.now());
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(activityRepository.findBySessionId(session.getId()))
                .thenReturn(Optional.of(mock(Activity.class)));

        assertThatThrownBy(() -> service().importToSession(USER, session.getId(), 7L))
                .isInstanceOf(StravaException.class)
                .hasMessageContaining("already has measures");
    }

    @Test
    void importToSession_rejectsAlreadyAttachedStravaActivity() {
        PlannedSession session = runSession(LocalDate.now());
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(activityRepository.findBySessionId(session.getId())).thenReturn(Optional.empty());
        when(activityRepository.findByExternalId(7L)).thenReturn(Optional.of(mock(Activity.class)));

        assertThatThrownBy(() -> service().importToSession(USER, session.getId(), 7L))
                .isInstanceOf(StravaException.class)
                .hasMessageContaining("already attached");
    }
}
