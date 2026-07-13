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

    public StravaWebhookService(StravaSyncService syncService,
                                StravaConnectionRepository connectionRepository) {
        this.syncService = syncService;
        this.connectionRepository = connectionRepository;
    }

    @Async
    public void process(StravaDtos.WebhookEvent event) {
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
