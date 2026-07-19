package com.cavale.integration.strava;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Processes Strava push events. The callback must answer within 2 seconds,
 * so the controller acks immediately and this runs async: resolve the
 * athlete from the event's owner id, then upsert (create/update) or drop
 * (delete) the activity. Unknown athletes and non-activity events are
 * ignored — Strava sends one event stream for the whole application.
 */
@Service
public class StravaWebhookService {

    private static final Logger log = LoggerFactory.getLogger(StravaWebhookService.class);

    private final StravaSyncService syncService;
    private final StravaConnectionRepository connectionRepository;
    private final StravaProperties properties;

    public StravaWebhookService(StravaSyncService syncService,
                                StravaConnectionRepository connectionRepository,
                                StravaProperties properties) {
        this.syncService = syncService;
        this.connectionRepository = connectionRepository;
        this.properties = properties;
    }

    @Async
    public void process(StravaDtos.WebhookEvent event) {
        // Strava does not sign webhook POSTs, so the only authenticity signal
        // is the subscription id. When we know ours, drop anything else —
        // otherwise an attacker who guesses a connected athlete's public id
        // could forge delete/create events against them.
        Long expectedSubscription = properties.webhookSubscriptionIdOrNull();
        if (expectedSubscription != null && expectedSubscription != event.subscriptionId()) {
            log.warn("Dropping Strava webhook with unexpected subscription {} (event {} {})",
                    event.subscriptionId(), event.aspectType(), event.objectId());
            return;
        }
        if (!"activity".equals(event.objectType())) {
            return;
        }
        StravaConnection connection = connectionRepository.findByAthleteId(event.ownerId())
                .orElse(null);
        if (connection == null) {
            return;
        }
        try {
            switch (event.aspectType()) {
                case "create", "update" ->
                        syncService.upsertFromStrava(connection.getUserId(), event.objectId());
                case "delete" ->
                        syncService.deleteFromStrava(connection.getUserId(), event.objectId());
                default -> { }
            }
            log.info("Strava webhook {} activity {} for athlete {}",
                    event.aspectType(), event.objectId(), event.ownerId());
        } catch (Exception e) {
            // the 15-min poll is the safety net — a lost event self-heals
            log.warn("Strava webhook {} activity {} failed: {}",
                    event.aspectType(), event.objectId(), e.getMessage());
        }
    }
}
