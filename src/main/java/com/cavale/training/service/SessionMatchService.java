package com.cavale.training.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cavale.training.domain.Activity;
import com.cavale.training.domain.Discipline;
import com.cavale.training.domain.PlannedSession;
import com.cavale.training.domain.SessionStatus;
import com.cavale.training.repository.ActivityRepository;
import com.cavale.training.repository.PlannedSessionRepository;

/**
 * Proposes the ingested Strava run that most likely realizes a planned
 * session, so validation becomes "yes, that's the one" instead of a picker.
 *
 * Matching rules, deliberately conservative — a wrong proposal costs more
 * trust than no proposal:
 * - same-day runs always qualify; the closest duration wins;
 * - runs 1–2 days off qualify only when the planned duration is known and
 *   the actual stays within {@value #MAX_OFFDAY_DEVIATION_PCT}% of it, and
 *   only if the run's own day has no unvalidated RUN session (that session
 *   claims it first).
 */
@Service
public class SessionMatchService {

    private static final int WINDOW_DAYS = 2;
    private static final int MAX_OFFDAY_DEVIATION_PCT = 40;
    /** A day of distance outweighs any duration mismatch within one day. */
    private static final int DAY_PENALTY = 100;

    private final ActivityRepository activityRepository;
    private final PlannedSessionRepository sessionRepository;

    public SessionMatchService(ActivityRepository activityRepository,
                               PlannedSessionRepository sessionRepository) {
        this.activityRepository = activityRepository;
        this.sessionRepository = sessionRepository;
    }

    @Transactional(readOnly = true)
    public Optional<Activity> proposeFor(PlannedSession session) {
        if (session.getDiscipline() != Discipline.RUN || session.getStatus() != SessionStatus.PLANNED) {
            return Optional.empty();
        }
        LocalDate from = session.getDate().minusDays(WINDOW_DAYS);
        LocalDate to = session.getDate().plusDays(WINDOW_DAYS);
        List<Activity> candidates = activityRepository
                .findByUserIdAndSessionIsNullAndDateBetween(session.getUserId(), from, to);
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        List<PlannedSession> nearbySessions = sessionRepository
                .findByUserIdAndDateBetweenOrderByDateAscOrderInDayAsc(session.getUserId(), from, to);

        return candidates.stream()
                .filter(a -> a.getExternalId() != null)
                .filter(a -> qualifies(session, a, nearbySessions))
                .min(Comparator.comparingInt(a -> score(session, a)));
    }

    private static boolean qualifies(PlannedSession session, Activity activity,
                                     List<PlannedSession> nearbySessions) {
        long days = Math.abs(ChronoUnit.DAYS.between(session.getDate(), activity.getDate()));
        if (days == 0) {
            return true;
        }
        if (session.getDurationMin() == null
                || durationDeviationPct(session.getDurationMin(), activity) > MAX_OFFDAY_DEVIATION_PCT) {
            return false;
        }
        // The run's own day claims it first: don't steal what fits another slot.
        return nearbySessions.stream().noneMatch(other -> !other.getId().equals(session.getId())
                && other.getDate().equals(activity.getDate())
                && other.getDiscipline() == Discipline.RUN
                && other.getStatus() == SessionStatus.PLANNED);
    }

    private static int score(PlannedSession session, Activity activity) {
        int days = (int) Math.abs(ChronoUnit.DAYS.between(session.getDate(), activity.getDate()));
        int duration = session.getDurationMin() != null
                ? durationDeviationPct(session.getDurationMin(), activity)
                : 0;
        return days * DAY_PENALTY + Math.min(duration, DAY_PENALTY - 1);
    }

    private static int durationDeviationPct(int plannedMin, Activity activity) {
        return Math.round(Math.abs(activity.getDurationMin() - plannedMin) * 100f / plannedMin);
    }
}
