package com.cavale.integration.strava;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class StravaWebhookServiceTest {

    private static final UUID USER = UUID.randomUUID();
    private static final long ATHLETE = 42L;

    @Mock
    private StravaSyncService syncService;

    @Mock
    private StravaConnectionRepository connectionRepository;

    private StravaWebhookService service() {
        return service(null);
    }

    /** @param subscriptionId configured expected subscription, or null to accept any. */
    private StravaWebhookService service(String subscriptionId) {
        StravaProperties props = new StravaProperties("id", "secret", "cb", "fe", "login",
                "https://a", "https://b", "https://cb/webhook", "verify", subscriptionId);
        return new StravaWebhookService(syncService, connectionRepository, props);
    }

    private void connected() {
        when(connectionRepository.findByAthleteId(ATHLETE)).thenReturn(Optional.of(
                new StravaConnection(USER, ATHLETE, "access", "refresh",
                        Instant.now().plusSeconds(3600), "activity:read_all")));
    }

    private static StravaDtos.WebhookEvent event(String objectType, String aspect) {
        return new StravaDtos.WebhookEvent(objectType, 9L, aspect, ATHLETE, 1L);
    }

    @Test
    void create_upsertsTheActivityForTheRightUser() {
        connected();
        service().process(event("activity", "create"));
        verify(syncService).upsertFromStrava(USER, 9L);
    }

    @Test
    void update_alsoUpserts() {
        connected();
        service().process(event("activity", "update"));
        verify(syncService).upsertFromStrava(USER, 9L);
    }

    @Test
    void delete_dropsTheActivity() {
        connected();
        service().process(event("activity", "delete"));
        verify(syncService).deleteFromStrava(USER, 9L);
    }

    @Test
    void unknownAthlete_isIgnored() {
        when(connectionRepository.findByAthleteId(ATHLETE)).thenReturn(Optional.empty());
        service().process(event("activity", "create"));
        verify(syncService, never()).upsertFromStrava(any(), anyLong());
    }

    @Test
    void athleteEvents_areIgnored() {
        service().process(event("athlete", "update"));
        verifyNoInteractions(syncService, connectionRepository);
    }

    @Test
    void syncFailure_isSwallowed() {
        connected();
        doThrow(new StravaException("rate limited")).when(syncService).upsertFromStrava(USER, 9L);
        service().process(event("activity", "create")); // must not throw — the poll self-heals
    }

    @Test
    void matchingSubscription_isProcessed() {
        connected();
        service("1").process(event("activity", "create")); // event carries subscription_id = 1
        verify(syncService).upsertFromStrava(USER, 9L);
    }

    @Test
    void mismatchedSubscription_isDropped() {
        // A forged event for a connected athlete but the wrong subscription id
        // must never reach the sync service (no data touched).
        service("999").process(event("activity", "delete"));
        verifyNoInteractions(syncService, connectionRepository);
    }
}
