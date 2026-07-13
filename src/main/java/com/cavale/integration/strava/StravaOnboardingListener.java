package com.cavale.integration.strava;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * The moment an account is linked to Strava, pull everything the athlete
 * brings along: the full run history (volume/statistics corpus) and its
 * best efforts (records, race estimations, MCP context) — no buttons.
 *
 * Runs AFTER the OAuth transaction commits (the connection row must be
 * visible) and async (the athlete's browser is mid-redirect). Records
 * analysis costs 1 request per activity, so only the first
 * {@value #ANALYZE_BATCHES} batches run here — the ingest scheduler
 * drains the rest, one batch per tick, within the rate budget.
 */
@Component
public class StravaOnboardingListener {

    private static final Logger log = LoggerFactory.getLogger(StravaOnboardingListener.class);

    /** 3 × 50 detail requests + list pages — safely inside 200 req / 15 min. */
    private static final int ANALYZE_BATCHES = 3;

    private final StravaSyncService syncService;

    public StravaOnboardingListener(StravaSyncService syncService) {
        this.syncService = syncService;
    }

    @Async
    @TransactionalEventListener
    public void onConnected(StravaConnectedEvent event) {
        try {
            StravaSyncService.SyncResult sync = syncService.syncHistory(event.userId());
            log.info("Strava onboarding: {} runs imported, {} enriched for user {}",
                    sync.imported(), sync.updated(), event.userId());

            for (int i = 0; i < ANALYZE_BATCHES; i++) {
                StravaSyncService.AnalyzeResult analyzed = syncService.analyzeRecords(event.userId());
                if (analyzed.remaining() == 0 || analyzed.analyzed() == 0) {
                    return; // done, or rate-limited — the scheduler takes over
                }
            }
        } catch (Exception e) {
            // the scheduler retries on its next tick — onboarding is self-healing
            log.warn("Strava onboarding sync failed for user {}: {}", event.userId(), e.getMessage());
        }
    }
}
