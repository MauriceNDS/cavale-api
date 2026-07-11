package com.cavale.integration.strava;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.cavale.training.domain.Activity;
import com.cavale.training.domain.ActivityBestEffort;
import com.cavale.training.repository.ActivityBestEffortRepository;
import com.cavale.training.repository.ActivityRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
    private ActivityRepository activityRepository;

    @Mock
    private ActivityBestEffortRepository bestEffortRepository;

    private StravaSyncService service() {
        return new StravaSyncService(authService, stravaClient, activityRepository, bestEffortRepository);
    }

    private static StravaConnection connection() {
        return new StravaConnection(USER, 42L, "access", "refresh",
                Instant.now().plusSeconds(3600), "activity:read_all");
    }

    private static StravaDtos.ActivitySummary summary(long id, String sport) {
        return new StravaDtos.ActivitySummary(id, "Sortie " + id, sport,
                LocalDateTime.of(LocalDate.of(2026, 5, 10), LocalTime.of(8, 0)),
                12000, 3600, 250, 148.0, 172.0, 84.0, 61.0);
    }

    @Test
    void syncHistory_importsRunsSkipsOtherSportsAndKnownActivities() {
        when(authService.freshConnection(USER)).thenReturn(connection());
        when(stravaClient.listActivitiesPage(anyString(), eq(1), anyInt())).thenReturn(List.of(
                summary(1L, "Run"), summary(2L, "Ride"), summary(3L, "TrailRun")));
        Activity known = Activity.stravaHistory(USER, LocalDate.of(2026, 5, 10), 60,
                new BigDecimal("12.00"), 250, 148, "Sortie 1", 1L);
        when(activityRepository.findByExternalIdIn(anyCollection())).thenReturn(List.of(known));

        StravaSyncService.SyncResult result = service().syncHistory(USER);

        assertThat(result.imported()).isEqualTo(1);
        assertThat(result.updated()).isEqualTo(1);
        assertThat(result.totalRuns()).isEqualTo(2);

        ArgumentCaptor<Activity> captor = ArgumentCaptor.forClass(Activity.class);
        verify(activityRepository).save(captor.capture());
        Activity imported = captor.getValue();
        assertThat(imported.getExternalId()).isEqualTo(3L);
        assertThat(imported.getSession()).isNull();
        // cadence stored as both-legs SPM, relative effort from suffer score
        assertThat(imported.getAvgCadenceSpm()).isEqualByComparingTo("168.0");
        assertThat(imported.getRelativeEffort()).isEqualTo(61);
        assertThat(imported.getMaxHr()).isEqualTo(172);
        // known one enriched, not recreated
        assertThat(known.getAvgCadenceSpm()).isEqualByComparingTo("168.0");
    }

    @Test
    void syncHistory_stopsOnShortPage() {
        when(authService.freshConnection(USER)).thenReturn(connection());
        when(stravaClient.listActivitiesPage(anyString(), eq(1), anyInt()))
                .thenReturn(List.of(summary(1L, "Run")));
        when(activityRepository.findByExternalIdIn(anyCollection())).thenReturn(List.of());

        service().syncHistory(USER);

        verify(stravaClient, times(1)).listActivitiesPage(anyString(), anyInt(), anyInt());
    }

    @Test
    void analyzeRecords_extractsBestEffortsAndMarksAnalyzed() {
        when(authService.freshConnection(USER)).thenReturn(connection());
        Activity activity = Activity.stravaHistory(USER, LocalDate.of(2026, 5, 10), 60,
                new BigDecimal("12.00"), 250, 148, "Sortie 9", 9L);
        when(activityRepository
                .findByUserIdAndExternalIdIsNotNullAndRecordsAnalyzedFalseOrderByDateAsc(eq(USER), any()))
                .thenReturn(List.of(activity));
        when(stravaClient.getActivity(anyString(), eq(9L))).thenReturn(new StravaDtos.ActivityDetail(
                9L, 84.0, 175.0, 63.0,
                List.of(new StravaDtos.BestEffort("1k", 1000, 255),
                        new StravaDtos.BestEffort("5k", 5000, 1450))));
        when(activityRepository.countByUserIdAndExternalIdIsNotNullAndRecordsAnalyzedFalse(USER))
                .thenReturn(0L);

        StravaSyncService.AnalyzeResult result = service().analyzeRecords(USER);

        assertThat(result.analyzed()).isEqualTo(1);
        assertThat(result.remaining()).isZero();
        assertThat(activity.isRecordsAnalyzed()).isTrue();

        ArgumentCaptor<ActivityBestEffort> captor = ArgumentCaptor.forClass(ActivityBestEffort.class);
        verify(bestEffortRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(ActivityBestEffort::getDistanceM)
                .containsExactly(1000, 5000);
        assertThat(captor.getAllValues().getFirst().getDate()).isEqualTo(LocalDate.of(2026, 5, 10));
    }

    @Test
    void analyzeRecords_stravaErrorStopsBatchButKeepsProgress() {
        when(authService.freshConnection(USER)).thenReturn(connection());
        Activity first = Activity.stravaHistory(USER, LocalDate.of(2026, 5, 10), 60,
                new BigDecimal("12.00"), 250, 148, "A", 1L);
        Activity second = Activity.stravaHistory(USER, LocalDate.of(2026, 5, 11), 60,
                new BigDecimal("12.00"), 250, 148, "B", 2L);
        when(activityRepository
                .findByUserIdAndExternalIdIsNotNullAndRecordsAnalyzedFalseOrderByDateAsc(eq(USER), any()))
                .thenReturn(List.of(first, second));
        when(stravaClient.getActivity(anyString(), eq(1L)))
                .thenReturn(new StravaDtos.ActivityDetail(1L, null, null, null, List.of()));
        when(stravaClient.getActivity(anyString(), eq(2L)))
                .thenThrow(new RuntimeException("429 Too Many Requests"));
        when(activityRepository.countByUserIdAndExternalIdIsNotNullAndRecordsAnalyzedFalse(USER))
                .thenReturn(1L);

        StravaSyncService.AnalyzeResult result = service().analyzeRecords(USER);

        assertThat(result.analyzed()).isEqualTo(1);
        assertThat(result.remaining()).isEqualTo(1);
        assertThat(first.isRecordsAnalyzed()).isTrue();
        assertThat(second.isRecordsAnalyzed()).isFalse();
        verify(bestEffortRepository, never()).save(any());
    }
}
