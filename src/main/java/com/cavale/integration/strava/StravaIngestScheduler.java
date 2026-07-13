package com.cavale.integration.strava;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Background ingestion: a finished run lands in Cavale as a standalone
 * activity minutes after Strava gets it, so session validation can PROPOSE
 * the matching run instead of sending the athlete to fetch it.
 *
 * Polling, not webhooks — no public callback URL needed on a self-hosted
 * box, and the cost stays negligible: one listActivities request per
 * connected athlete per tick, far under Strava's 200 req / 15 min budget.
 * Each athlete syncs in their own transaction; one failing (expired token,
 * rate limit) never blocks the others.
 */
@Component
public class StravaIngestScheduler {

    private static final Logger log = LoggerFactory.getLogger(StravaIngestScheduler.class);

    private final StravaSyncService syncService;
    private final StravaConnectionRepository connectionRepository;
    private final StravaProperties properties;

    public StravaIngestScheduler(StravaSyncService syncService,
                                 StravaConnectionRepository connectionRepository,
                                 StravaProperties properties) {
        this.syncService = syncService;
        this.connectionRepository = connectionRepository;
        this.properties = properties;
    }

    @Scheduled(initialDelayString = "${cavale.strava.ingest-initial-delay:PT2M}",
               fixedDelayString = "${cavale.strava.ingest-interval:PT15M}")
    public void ingestAll() {
        if (!properties.configured()) {
            return;
        }
        for (StravaConnection connection : connectionRepository.findAll()) {
            try {
                StravaSyncService.SyncResult result = syncService.syncRecent(connection.getUserId());
                if (result.imported() > 0) {
                    log.info("Strava ingest: {} new run(s) for athlete {}",
                            result.imported(), connection.getAthleteId());
                }
                // drain pending records analysis, one batch per tick (rate budget)
                StravaSyncService.AnalyzeResult analyzed = syncService.analyzeRecords(connection.getUserId());
                if (analyzed.analyzed() > 0) {
                    log.info("Strava ingest: {} record(s) analyzed for athlete {}, {} remaining",
                            analyzed.analyzed(), connection.getAthleteId(), analyzed.remaining());
                }
            } catch (Exception e) {
                log.warn("Strava ingest failed for athlete {}: {}",
                        connection.getAthleteId(), e.getMessage());
            }
        }
    }
}
