package com.cavale.integration.strava;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StravaOnboardingListenerTest {

    private static final UUID USER = UUID.randomUUID();

    @Mock
    private StravaSyncService syncService;

    private StravaOnboardingListener listener() {
        return new StravaOnboardingListener(syncService);
    }

    @Test
    void onConnected_pullsHistoryAndDrainsRecordsUntilDone() {
        when(syncService.syncHistory(USER))
                .thenReturn(new StravaSyncService.SyncResult(320, 0, 320));
        when(syncService.analyzeRecords(USER))
                .thenReturn(new StravaSyncService.AnalyzeResult(50, 270))
                .thenReturn(new StravaSyncService.AnalyzeResult(50, 220))
                .thenReturn(new StravaSyncService.AnalyzeResult(50, 170));

        listener().onConnected(new StravaConnectedEvent(USER));

        verify(syncService).syncHistory(USER);
        // 3 batches max on connect — the ingest scheduler drains the rest
        verify(syncService, times(3)).analyzeRecords(USER);
    }

    @Test
    void onConnected_stopsEarlyWhenEverythingIsAnalyzed() {
        when(syncService.syncHistory(USER))
                .thenReturn(new StravaSyncService.SyncResult(12, 0, 12));
        when(syncService.analyzeRecords(USER))
                .thenReturn(new StravaSyncService.AnalyzeResult(12, 0));

        listener().onConnected(new StravaConnectedEvent(USER));

        verify(syncService, times(1)).analyzeRecords(USER);
    }

    @Test
    void onConnected_stopsWhenRateLimited() {
        when(syncService.syncHistory(USER))
                .thenReturn(new StravaSyncService.SyncResult(320, 0, 320));
        // rate-limit inside a batch: nothing analyzed, plenty remaining
        when(syncService.analyzeRecords(USER))
                .thenReturn(new StravaSyncService.AnalyzeResult(0, 320));

        listener().onConnected(new StravaConnectedEvent(USER));

        verify(syncService, times(1)).analyzeRecords(USER);
    }

    @Test
    void onConnected_survivesSyncFailure() {
        when(syncService.syncHistory(USER)).thenThrow(new StravaException("Strava is down"));
        listener().onConnected(new StravaConnectedEvent(USER)); // must not throw
    }
}
