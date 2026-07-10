package com.cavale.integration.strava;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cavale.common.exception.ResourceNotFoundException;
import com.cavale.training.domain.Activity;
import com.cavale.training.domain.Discipline;
import com.cavale.training.domain.PlannedSession;
import com.cavale.training.domain.SessionStatus;
import com.cavale.training.repository.ActivityRepository;
import com.cavale.training.repository.PlannedSessionRepository;

/**
 * Session validation via Strava, user-driven: list the recent runs that were
 * NOT imported yet, let the user attach one to a planned session. No blind
 * bulk sync — the athlete decides what counts.
 */
@Service
public class StravaActivityService {

    private static final Set<String> RUN_SPORTS = Set.of("Run", "TrailRun", "VirtualRun");
    private static final int WINDOW_DAYS = 30;

    private final StravaAuthService authService;
    private final StravaClient stravaClient;
    private final PlannedSessionRepository sessionRepository;
    private final ActivityRepository activityRepository;

    public StravaActivityService(StravaAuthService authService, StravaClient stravaClient,
                                 PlannedSessionRepository sessionRepository,
                                 ActivityRepository activityRepository) {
        this.authService = authService;
        this.stravaClient = stravaClient;
        this.sessionRepository = sessionRepository;
        this.activityRepository = activityRepository;
    }

    public record StravaActivityOption(long id, String name, LocalDate date, int durationMin,
                                       BigDecimal distanceKm, Integer elevationM, Integer avgHr) {
    }

    @Transactional(readOnly = true)
    public List<StravaActivityOption> listRecentNotImported(UUID userId) {
        List<StravaDtos.ActivitySummary> runs = fetchRecentRuns(userId);

        Set<Long> importedIds = activityRepository
                .findByExternalIdIn(runs.stream().map(StravaDtos.ActivitySummary::id).toList())
                .stream().map(Activity::getExternalId).collect(Collectors.toSet());

        return runs.stream()
                .filter(run -> !importedIds.contains(run.id()))
                .sorted(Comparator.comparing(StravaDtos.ActivitySummary::startDateLocal).reversed())
                .map(StravaActivityService::toOption)
                .toList();
    }

    @Transactional
    public Activity importToSession(UUID userId, UUID sessionId, long stravaActivityId) {
        PlannedSession session = sessionRepository.findById(sessionId)
                .filter(s -> s.getUserId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Session", sessionId));
        if (session.getDiscipline() != Discipline.RUN) {
            throw new StravaException("Only running sessions can import a Strava activity");
        }
        if (activityRepository.findBySessionId(session.getId()).isPresent()) {
            throw new StravaException("This session already has measures — reset it first");
        }
        if (activityRepository.findByExternalId(stravaActivityId).isPresent()) {
            throw new StravaException("This Strava activity is already attached to another session");
        }

        StravaDtos.ActivitySummary run = fetchRecentRuns(userId).stream()
                .filter(a -> a.id() == stravaActivityId)
                .findFirst()
                .orElseThrow(() -> new StravaException("Strava activity not found in the recent window"));

        Activity activity = activityRepository.save(Activity.fromStrava(session,
                run.startDateLocal().toLocalDate(),
                Math.max(1, Math.round(run.movingTime() / 60f)),
                BigDecimal.valueOf(run.distance() / 1000.0).setScale(2, RoundingMode.HALF_UP),
                (int) Math.round(run.totalElevationGain()),
                run.averageHeartrate() != null ? (int) Math.round(run.averageHeartrate()) : null,
                run.name(), run.id()));
        session.updateStatus(SessionStatus.DONE);
        return activity;
    }

    private List<StravaDtos.ActivitySummary> fetchRecentRuns(UUID userId) {
        StravaConnection connection = authService.freshConnection(userId);
        Instant now = Instant.now();
        return stravaClient.listActivities(connection.getAccessToken(),
                        now.minusSeconds(WINDOW_DAYS * 86400L), now).stream()
                .filter(a -> RUN_SPORTS.contains(a.sportType()))
                .toList();
    }

    private static StravaActivityOption toOption(StravaDtos.ActivitySummary run) {
        return new StravaActivityOption(run.id(), run.name(), run.startDateLocal().toLocalDate(),
                Math.max(1, Math.round(run.movingTime() / 60f)),
                BigDecimal.valueOf(run.distance() / 1000.0).setScale(2, RoundingMode.HALF_UP),
                (int) Math.round(run.totalElevationGain()),
                run.averageHeartrate() != null ? (int) Math.round(run.averageHeartrate()) : null);
    }
}
