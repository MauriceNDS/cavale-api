package com.cavale.integration.intervals;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Keeps every connected athlete's Intervals.icu calendar aligned with their
 * Cavale plan. Because pushes upsert on external_id, each tick converges the
 * calendar (new sessions appear, adapted ones update) — so a plan changed in
 * Cavale reaches the watch within one tick without any manual action.
 * Each athlete pushes in isolation; one failing key never blocks the others.
 */
@Component
public class IntervalsPushScheduler {

    private static final Logger log = LoggerFactory.getLogger(IntervalsPushScheduler.class);

    private final IntervalsService service;
    private final IntervalsConnectionRepository connectionRepository;

    public IntervalsPushScheduler(IntervalsService service,
                                  IntervalsConnectionRepository connectionRepository) {
        this.service = service;
        this.connectionRepository = connectionRepository;
    }

    @Scheduled(initialDelayString = "${cavale.intervals.push-initial-delay:PT3M}",
               fixedDelayString = "${cavale.intervals.push-interval:PT6H}")
    public void pushAll() {
        for (IntervalsConnection connection : connectionRepository.findAll()) {
            try {
                IntervalsService.PushResult result = service.pushUpcoming(connection.getUserId());
                if (result.pushed() > 0) {
                    log.info("Intervals push: {} workout(s) for athlete {}",
                            result.pushed(), connection.getAthleteId());
                }
            } catch (Exception e) {
                log.warn("Intervals push failed for athlete {}: {}",
                        connection.getAthleteId(), e.getMessage());
            }
        }
    }
}
