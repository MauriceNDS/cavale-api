package com.cavale.integration.strava;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cavale.training.domain.Activity;
import com.cavale.training.domain.ActivityBestEffort;
import com.cavale.training.repository.ActivityBestEffortRepository;
import com.cavale.training.repository.ActivityRepository;

/**
 * Strava import into the activity corpus. Three entry points, all idempotent
 * and rate-limit-aware (Strava: 200 requests / 15 min):
 *
 * 1. {@link #syncHistory} — user-driven: pages through the athlete's whole
 *    activity list (1 request per 200 activities) and upserts every run as an
 *    Activity; runs already imported (standalone or attached to a session)
 *    are only enriched with the newer summary metrics.
 * 2. {@link #syncRecent} — background: the windowed variant behind the
 *    ingest scheduler (1 request), so a finished run lands in Cavale without
 *    the athlete doing anything.
 * 3. {@link #analyzeRecords} — fetches per-activity detail (1 request each,
 *    hence batched) to extract Strava's best efforts, which feed the
 *    distance records and race-time estimations.
 */
@Service
public class StravaSyncService {

    private static final Logger log = LoggerFactory.getLogger(StravaSyncService.class);

    static final Set<String> RUN_SPORTS = Set.of("Run", "TrailRun", "VirtualRun");
    private static final int PAGE_SIZE = 200;
    private static final int MAX_PAGES = 100;
    private static final int ANALYZE_BATCH = 50;
    /** Re-scanned before lastSyncAt — catches late uploads and settling metrics. */
    private static final Duration RESYNC_MARGIN = Duration.ofDays(3);
    /** First background sync of a never-synced athlete looks this far back. */
    private static final Duration FIRST_SYNC_WINDOW = Duration.ofDays(30);

    private final StravaAuthService authService;
    private final StravaClient stravaClient;
    private final ActivityRepository activityRepository;
    private final ActivityBestEffortRepository bestEffortRepository;

    public StravaSyncService(StravaAuthService authService, StravaClient stravaClient,
                             ActivityRepository activityRepository,
                             ActivityBestEffortRepository bestEffortRepository) {
        this.authService = authService;
        this.stravaClient = stravaClient;
        this.activityRepository = activityRepository;
        this.bestEffortRepository = bestEffortRepository;
    }

    public record SyncResult(int imported, int updated, int totalRuns) {
    }

    public record AnalyzeResult(int analyzed, long remaining) {
    }

    @Transactional
    public SyncResult syncHistory(UUID userId) {
        StravaConnection connection = authService.freshConnection(userId);
        int imported = 0;
        int updated = 0;
        int totalRuns = 0;

        for (int page = 1; page <= MAX_PAGES; page++) {
            List<StravaDtos.ActivitySummary> batch =
                    stravaClient.listActivitiesPage(connection.getAccessToken(), page, PAGE_SIZE);
            if (batch == null || batch.isEmpty()) {
                break;
            }
            List<StravaDtos.ActivitySummary> runs = batch.stream()
                    .filter(a -> RUN_SPORTS.contains(a.sportType()))
                    .toList();
            totalRuns += runs.size();

            UpsertCounts counts = upsertRuns(userId, runs);
            imported += counts.imported();
            updated += counts.updated();
            if (batch.size() < PAGE_SIZE) {
                break;
            }
        }

        connection.markSynced(Instant.now());
        return new SyncResult(imported, updated, totalRuns);
    }

    /**
     * Windowed upsert of recent runs: since the last sync (re-scanning a
     * margin before it — a watch can upload hours after the run ended, and
     * summary metrics settle late), or a first bootstrap window when the
     * athlete was never synced.
     */
    @Transactional
    public SyncResult syncRecent(UUID userId) {
        StravaConnection connection = authService.freshConnection(userId);
        Instant now = Instant.now();
        Instant after = connection.getLastSyncAt() != null
                ? connection.getLastSyncAt().minus(RESYNC_MARGIN)
                : now.minus(FIRST_SYNC_WINDOW);

        List<StravaDtos.ActivitySummary> runs = stravaClient
                .listActivities(connection.getAccessToken(), after, now).stream()
                .filter(a -> RUN_SPORTS.contains(a.sportType()))
                .toList();

        UpsertCounts counts = upsertRuns(userId, runs);
        connection.markSynced(now);
        return new SyncResult(counts.imported(), counts.updated(), runs.size());
    }

    private record UpsertCounts(int imported, int updated) {
    }

    /** New runs become standalone history rows; known ones only gain metrics. */
    private UpsertCounts upsertRuns(UUID userId, List<StravaDtos.ActivitySummary> runs) {
        if (runs.isEmpty()) {
            return new UpsertCounts(0, 0);
        }
        Map<Long, Activity> existing = activityRepository
                .findByExternalIdIn(runs.stream().map(StravaDtos.ActivitySummary::id).toList())
                .stream()
                .collect(Collectors.toMap(Activity::getExternalId, a -> a));

        int imported = 0;
        int updated = 0;
        for (StravaDtos.ActivitySummary run : runs) {
            Activity known = existing.get(run.id());
            if (known != null) {
                known.enrich(cadenceSpm(run.averageCadence()),
                        toInt(run.sufferScore()), toInt(run.maxHeartrate()));
                updated++;
                continue;
            }
            activityRepository.save(newHistoryActivity(userId, run));
            imported++;
        }
        return new UpsertCounts(imported, updated);
    }

    /**
     * Analyzes the next batch of activities; call repeatedly until
     * {@code remaining} reaches 0. A Strava error (rate limit…) stops the
     * batch but keeps what was already analyzed.
     */
    @Transactional
    public AnalyzeResult analyzeRecords(UUID userId) {
        StravaConnection connection = authService.freshConnection(userId);
        List<Activity> pending = activityRepository
                .findByUserIdAndExternalIdIsNotNullAndRecordsAnalyzedFalseOrderByDateAsc(
                        userId, Limit.of(ANALYZE_BATCH));

        int analyzed = 0;
        for (Activity activity : pending) {
            StravaDtos.ActivityDetail detail;
            try {
                detail = stravaClient.getActivity(connection.getAccessToken(), activity.getExternalId());
            } catch (Exception e) {
                log.warn("Records analysis stopped after {} activities: {}", analyzed, e.getMessage());
                break;
            }
            if (detail != null) {
                activity.enrich(cadenceSpm(detail.averageCadence()),
                        toInt(detail.sufferScore()), toInt(detail.maxHeartrate()));
                for (StravaDtos.BestEffort effort : detail.bestEfforts() != null
                        ? detail.bestEfforts() : List.<StravaDtos.BestEffort>of()) {
                    bestEffortRepository.save(new ActivityBestEffort(activity, effort.name(),
                            (int) Math.round(effort.distance()), effort.elapsedTime()));
                }
            }
            activity.markRecordsAnalyzed();
            analyzed++;
        }

        long remaining = activityRepository.countByUserIdAndExternalIdIsNotNullAndRecordsAnalyzedFalse(userId);
        return new AnalyzeResult(analyzed, remaining);
    }

    private static Activity newHistoryActivity(UUID userId, StravaDtos.ActivitySummary run) {
        Activity activity = Activity.stravaHistory(userId,
                run.startDateLocal().toLocalDate(),
                Math.max(1, Math.round(run.movingTime() / 60f)),
                BigDecimal.valueOf(run.distance() / 1000.0).setScale(2, RoundingMode.HALF_UP),
                (int) Math.round(run.totalElevationGain()),
                toInt(run.averageHeartrate()),
                run.name(), run.id());
        activity.enrich(cadenceSpm(run.averageCadence()), toInt(run.sufferScore()), toInt(run.maxHeartrate()));
        return activity;
    }

    /** Strava reports run cadence per leg; the usual SPM figure counts both. */
    static BigDecimal cadenceSpm(Double perLeg) {
        if (perLeg == null || perLeg == 0) {
            return null;
        }
        return BigDecimal.valueOf(perLeg * 2).setScale(1, RoundingMode.HALF_UP);
    }

    private static Integer toInt(Double value) {
        return value != null ? (int) Math.round(value) : null;
    }
}
