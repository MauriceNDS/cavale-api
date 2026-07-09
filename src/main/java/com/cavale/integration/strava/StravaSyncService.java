package com.cavale.integration.strava;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cavale.training.domain.Activity;
import com.cavale.training.domain.Discipline;
import com.cavale.training.domain.PlannedSession;
import com.cavale.training.domain.SessionStatus;
import com.cavale.training.repository.ActivityRepository;
import com.cavale.training.repository.PlannedSessionRepository;

/**
 * Pulls recent Strava runs and matches each one to a planned session on the
 * same date (nearest planned duration wins). A match validates the session:
 * the run becomes its STRAVA activity and the session goes DONE. Re-syncs are
 * idempotent via the Strava activity id.
 */
@Service
public class StravaSyncService {

    private static final Set<String> RUN_SPORTS = Set.of("Run", "TrailRun", "VirtualRun");
    private static final int DEFAULT_WINDOW_DAYS = 30;

    private final StravaAuthService authService;
    private final StravaClient stravaClient;
    private final PlannedSessionRepository sessionRepository;
    private final ActivityRepository activityRepository;

    public StravaSyncService(StravaAuthService authService, StravaClient stravaClient,
                             PlannedSessionRepository sessionRepository,
                             ActivityRepository activityRepository) {
        this.authService = authService;
        this.stravaClient = stravaClient;
        this.sessionRepository = sessionRepository;
        this.activityRepository = activityRepository;
    }

    public record SyncResult(int fetched, int matched, int alreadyImported, int unmatched) {
    }

    @Transactional
    public SyncResult sync(UUID userId) {
        StravaConnection connection = authService.freshConnection(userId);

        Instant now = Instant.now();
        Instant after = now.minusSeconds(DEFAULT_WINDOW_DAYS * 86400L);
        List<StravaDtos.ActivitySummary> stravaActivities =
                stravaClient.listActivities(connection.getAccessToken(), after, now);

        LocalDate from = LocalDate.ofInstant(after, ZoneId.systemDefault());
        LocalDate to = LocalDate.ofInstant(now, ZoneId.systemDefault());
        List<PlannedSession> candidates = sessionRepository
                .findByUserIdAndDateBetweenOrderByDateAscOrderInDayAsc(userId, from, to).stream()
                .filter(s -> s.getDiscipline() == Discipline.RUN)
                .toList();

        int matched = 0;
        int alreadyImported = 0;
        int unmatched = 0;

        for (StravaDtos.ActivitySummary strava : stravaActivities) {
            if (!RUN_SPORTS.contains(strava.sportType())) {
                continue;
            }
            if (activityRepository.findByExternalId(strava.id()).isPresent()) {
                alreadyImported++;
                continue;
            }

            Optional<PlannedSession> match = bestMatch(candidates, strava);
            if (match.isEmpty()) {
                unmatched++;
                continue;
            }

            PlannedSession session = match.get();
            int durationMin = Math.max(1, Math.round(strava.movingTime() / 60f));
            BigDecimal distanceKm = BigDecimal.valueOf(strava.distance() / 1000.0)
                    .setScale(2, RoundingMode.HALF_UP);
            Integer elevationM = (int) Math.round(strava.totalElevationGain());
            Integer avgHr = strava.averageHeartrate() != null
                    ? (int) Math.round(strava.averageHeartrate())
                    : null;

            activityRepository.save(Activity.fromStrava(session, strava.startDateLocal().toLocalDate(),
                    durationMin, distanceKm, elevationM, avgHr, strava.name(), strava.id()));
            session.updateStatus(SessionStatus.DONE);
            matched++;
        }

        connection.markSynced(now);
        return new SyncResult(stravaActivities.size(), matched, alreadyImported, unmatched);
    }

    /**
     * Same-date RUN session without an activity yet; when several qualify
     * (double days), the one whose planned duration is closest wins.
     */
    private Optional<PlannedSession> bestMatch(List<PlannedSession> candidates,
                                               StravaDtos.ActivitySummary strava) {
        LocalDate date = strava.startDateLocal().toLocalDate();
        int actualMin = Math.round(strava.movingTime() / 60f);
        return candidates.stream()
                .filter(s -> s.getDate().equals(date))
                .filter(s -> activityRepository.findBySessionId(s.getId()).isEmpty())
                .min(Comparator.comparingInt(s ->
                        Math.abs((s.getDurationMin() != null ? s.getDurationMin() : actualMin) - actualMin)));
    }
}
