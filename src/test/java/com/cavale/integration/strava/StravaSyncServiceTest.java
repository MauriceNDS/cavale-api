package com.cavale.integration.strava;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StravaSyncServiceTest {

    private static final UUID USER = UUID.randomUUID();

    @Mock
    private StravaAuthService authService;

    @Mock
    private StravaClient stravaClient;

    @Mock
    private PlannedSessionRepository sessionRepository;

    @Mock
    private ActivityRepository activityRepository;

    private StravaSyncService service() {
        return new StravaSyncService(authService, stravaClient, sessionRepository, activityRepository);
    }

    private static StravaConnection connection() {
        return new StravaConnection(USER, 42L, "access", "refresh",
                Instant.now().plusSeconds(3600), "activity:read_all");
    }

    private static PlannedSession runSession(LocalDate date, Integer durationMin, String title) {
        TrainingPlan plan = new TrainingPlan(USER, "Plan", null,
                date.minusDays(30), date.plusDays(30));
        PlanWeek week = new PlanWeek(plan, 1, date.minusDays(date.getDayOfWeek().getValue() - 1L),
                null, WeekType.BUILD, null, null, null, null);
        PlannedSession session = new PlannedSession(week, USER, date, 0, Discipline.RUN,
                title, null, "EF", durationMin, null, 2, 3);
        ReflectionTestUtils.setField(session, "id", UUID.randomUUID());
        return session;
    }

    private static StravaDtos.ActivitySummary stravaRun(long id, LocalDate date, int minutes,
                                                        double distanceM, String sport) {
        return new StravaDtos.ActivitySummary(id, "Morning Run", sport,
                LocalDateTime.of(date, java.time.LocalTime.of(7, 30)),
                distanceM, minutes * 60, 210.4, 148.6);
    }

    @Test
    void sync_matchesRunToSameDateSessionAndValidatesIt() {
        LocalDate date = LocalDate.now().minusDays(2);
        PlannedSession session = runSession(date, 60, "EF 60′");

        when(authService.freshConnection(USER)).thenReturn(connection());
        when(stravaClient.listActivities(anyString(), any(), any()))
                .thenReturn(List.of(stravaRun(9001L, date, 58, 10240, "Run")));
        when(sessionRepository.findByUserIdAndDateBetweenOrderByDateAscOrderInDayAsc(any(), any(), any()))
                .thenReturn(List.of(session));
        when(activityRepository.findByExternalId(9001L)).thenReturn(Optional.empty());
        when(activityRepository.findBySessionId(session.getId())).thenReturn(Optional.empty());
        when(activityRepository.save(any(Activity.class))).thenAnswer(inv -> inv.getArgument(0));

        StravaSyncService.SyncResult result = service().sync(USER);

        assertThat(result.matched()).isEqualTo(1);
        assertThat(result.unmatched()).isZero();
        assertThat(session.getStatus()).isEqualTo(SessionStatus.DONE);

        ArgumentCaptor<Activity> captor = ArgumentCaptor.forClass(Activity.class);
        verify(activityRepository).save(captor.capture());
        Activity saved = captor.getValue();
        assertThat(saved.getSource()).isEqualTo(ActivitySource.STRAVA);
        assertThat(saved.getExternalId()).isEqualTo(9001L);
        assertThat(saved.getDurationMin()).isEqualTo(58);
        assertThat(saved.getDistanceKm()).isEqualByComparingTo(new BigDecimal("10.24"));
        assertThat(saved.getElevationM()).isEqualTo(210);
        assertThat(saved.getAvgHr()).isEqualTo(149);
    }

    @Test
    void sync_picksNearestDurationOnDoubleDays() {
        LocalDate date = LocalDate.now().minusDays(3);
        PlannedSession shortRun = runSession(date, 45, "EF 45′");
        PlannedSession longRun = runSession(date, 210, "SL 3h30");

        when(authService.freshConnection(USER)).thenReturn(connection());
        when(stravaClient.listActivities(anyString(), any(), any()))
                .thenReturn(List.of(stravaRun(9002L, date, 205, 32000, "TrailRun")));
        when(sessionRepository.findByUserIdAndDateBetweenOrderByDateAscOrderInDayAsc(any(), any(), any()))
                .thenReturn(List.of(shortRun, longRun));
        when(activityRepository.findByExternalId(9002L)).thenReturn(Optional.empty());
        when(activityRepository.findBySessionId(any())).thenReturn(Optional.empty());
        when(activityRepository.save(any(Activity.class))).thenAnswer(inv -> inv.getArgument(0));

        service().sync(USER);

        assertThat(longRun.getStatus()).isEqualTo(SessionStatus.DONE);
        assertThat(shortRun.getStatus()).isEqualTo(SessionStatus.PLANNED);
    }

    @Test
    void sync_isIdempotentAndCountsNonRunsOut() {
        LocalDate date = LocalDate.now().minusDays(1);

        when(authService.freshConnection(USER)).thenReturn(connection());
        when(stravaClient.listActivities(anyString(), any(), any())).thenReturn(List.of(
                stravaRun(9003L, date, 40, 7000, "Run"),      // already imported
                stravaRun(9004L, date, 90, 25000, "Ride"),    // not a run
                stravaRun(9005L, date.minusDays(1), 50, 9000, "Run"))); // no session that day
        when(sessionRepository.findByUserIdAndDateBetweenOrderByDateAscOrderInDayAsc(any(), any(), any()))
                .thenReturn(List.of());
        when(activityRepository.findByExternalId(9003L))
                .thenReturn(Optional.of(org.mockito.Mockito.mock(Activity.class)));
        when(activityRepository.findByExternalId(9005L)).thenReturn(Optional.empty());

        StravaSyncService.SyncResult result = service().sync(USER);

        assertThat(result.fetched()).isEqualTo(3);
        assertThat(result.matched()).isZero();
        assertThat(result.alreadyImported()).isEqualTo(1);
        assertThat(result.unmatched()).isEqualTo(1);
    }
}
