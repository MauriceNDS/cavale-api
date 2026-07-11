package com.cavale.training.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cavale.training.domain.Activity;
import com.cavale.training.domain.Discipline;
import com.cavale.training.domain.Objective;
import com.cavale.training.domain.ObjectiveRole;
import com.cavale.training.domain.PlanWeek;
import com.cavale.training.domain.PlannedSession;
import com.cavale.training.domain.SessionStatus;
import com.cavale.training.domain.TrainingPlan;
import com.cavale.training.dto.ObjectiveResponse;
import com.cavale.training.dto.PlanProgressResponse;
import com.cavale.training.dto.PlanProgressResponse.ProgressTotals;
import com.cavale.training.dto.PlanProgressResponse.WeekProgress;
import com.cavale.training.dto.PlanResponse;
import com.cavale.training.repository.ActivityRepository;
import com.cavale.training.repository.ObjectiveRepository;
import com.cavale.training.repository.PlanWeekRepository;
import com.cavale.training.repository.PlannedSessionRepository;

/**
 * Read model for the objective page: target-vs-actual aggregates over one
 * plan. Actual volume/D+ come from recorded activities; actual duration
 * falls back to the planned duration for DONE sessions without one (gym
 * sessions are validated by status alone). REST days never count as sessions.
 */
@Service
public class PlanProgressService {

    private static final int DAYS_PER_WEEK = 7;

    private final TrainingPlanService planService;
    private final PlanWeekRepository weekRepository;
    private final PlannedSessionRepository sessionRepository;
    private final ActivityRepository activityRepository;
    private final ObjectiveRepository objectiveRepository;

    public PlanProgressService(TrainingPlanService planService,
                               PlanWeekRepository weekRepository,
                               PlannedSessionRepository sessionRepository,
                               ActivityRepository activityRepository,
                               ObjectiveRepository objectiveRepository) {
        this.planService = planService;
        this.weekRepository = weekRepository;
        this.sessionRepository = sessionRepository;
        this.activityRepository = activityRepository;
        this.objectiveRepository = objectiveRepository;
    }

    @Transactional(readOnly = true)
    public PlanProgressResponse getProgress(UUID userId, UUID planId) {
        return getProgress(userId, planId, LocalDate.now());
    }

    @Transactional(readOnly = true)
    public PlanProgressResponse getProgress(UUID userId, UUID planId, LocalDate today) {
        TrainingPlan plan = planService.getOwnedPlan(userId, planId);
        List<PlanWeek> weeks = weekRepository.findByPlanIdOrderByWeekNumber(planId);
        List<PlannedSession> sessions = sessionRepository.findByWeekPlanId(planId);
        Map<UUID, Activity> activitiesBySession = activitiesFor(sessions);
        List<Objective> objectives = ObjectiveService.sorted(objectiveRepository.findByPlanId(planId));

        Objective main = objectives.stream()
                .filter(o -> o.getRole() == ObjectiveRole.MAIN)
                .findFirst()
                .orElse(null);
        List<ObjectiveResponse> secondaries = objectives.stream()
                .filter(o -> o.getRole() == ObjectiveRole.SECONDARY)
                .map(ObjectiveResponse::from)
                .toList();

        Map<UUID, List<PlannedSession>> sessionsByWeek = sessions.stream()
                .collect(Collectors.groupingBy(s -> s.getWeek().getId()));

        List<WeekProgress> weekRows = weeks.stream()
                .map(week -> weekProgress(week, sessionsByWeek.getOrDefault(week.getId(), List.of()),
                        activitiesBySession, today))
                .toList();

        Integer currentWeekNumber = weekRows.stream()
                .filter(WeekProgress::current)
                .map(WeekProgress::weekNumber)
                .findFirst()
                .orElse(null);

        LocalDate objectiveDate = main != null && main.getDate() != null ? main.getDate() : plan.getEndDate();
        int daysToObjective = (int) ChronoUnit.DAYS.between(today, objectiveDate);

        return new PlanProgressResponse(
                PlanResponse.from(plan),
                main != null ? ObjectiveResponse.from(main) : null,
                secondaries,
                weeks.size(),
                currentWeekNumber,
                daysToObjective,
                totals(weeks, sessions, activitiesBySession, today),
                weekRows);
    }

    private Map<UUID, Activity> activitiesFor(List<PlannedSession> sessions) {
        if (sessions.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = sessions.stream().map(PlannedSession::getId).toList();
        return activityRepository.findBySessionIdIn(ids).stream()
                .collect(Collectors.toMap(a -> a.getSession().getId(), a -> a));
    }

    private static WeekProgress weekProgress(PlanWeek week, List<PlannedSession> sessions,
                                             Map<UUID, Activity> activities, LocalDate today) {
        List<PlannedSession> real = withoutRest(sessions);
        boolean current = !today.isBefore(week.getStartDate())
                && today.isBefore(week.getStartDate().plusDays(DAYS_PER_WEEK));
        return new WeekProgress(
                week.getId(),
                week.getWeekNumber(),
                week.getStartDate(),
                week.getWeekType(),
                week.getPhase(),
                current,
                week.getTargetVolumeKm(),
                week.getTargetElevationM(),
                week.getTargetLoadUa(),
                actualVolumeKm(real, activities),
                actualElevationM(real, activities),
                actualDurationMin(real, activities),
                real.size(),
                countByStatus(real, SessionStatus.DONE),
                countByStatus(real, SessionStatus.SKIPPED));
    }

    private static ProgressTotals totals(List<PlanWeek> weeks, List<PlannedSession> sessions,
                                         Map<UUID, Activity> activities, LocalDate today) {
        List<PlannedSession> real = withoutRest(sessions);
        List<PlannedSession> past = real.stream().filter(s -> s.getDate().isBefore(today)).toList();

        BigDecimal plannedVolumeToDate = BigDecimal.ZERO;
        BigDecimal plannedElevationToDate = BigDecimal.ZERO;
        BigDecimal targetVolume = BigDecimal.ZERO;
        int targetElevation = 0;
        for (PlanWeek week : weeks) {
            BigDecimal volume = week.getTargetVolumeKm() != null ? week.getTargetVolumeKm() : BigDecimal.ZERO;
            int elevation = week.getTargetElevationM() != null ? week.getTargetElevationM() : 0;
            targetVolume = targetVolume.add(volume);
            targetElevation += elevation;
            BigDecimal share = elapsedShare(week, today);
            plannedVolumeToDate = plannedVolumeToDate.add(volume.multiply(share));
            plannedElevationToDate = plannedElevationToDate.add(BigDecimal.valueOf(elevation).multiply(share));
        }

        return new ProgressTotals(
                real.size(),
                countByStatus(real, SessionStatus.DONE),
                countByStatus(real, SessionStatus.SKIPPED),
                past.size(),
                countByStatus(past, SessionStatus.DONE),
                actualVolumeKm(real, activities),
                actualElevationM(real, activities),
                actualDurationMin(real, activities),
                plannedVolumeToDate.setScale(1, RoundingMode.HALF_UP),
                plannedElevationToDate.setScale(0, RoundingMode.HALF_UP).intValue(),
                targetVolume,
                targetElevation);
    }

    /** How much of a week has elapsed: 1 once it's over, day-prorated while running. */
    private static BigDecimal elapsedShare(PlanWeek week, LocalDate today) {
        long elapsedDays = ChronoUnit.DAYS.between(week.getStartDate(), today);
        long boundedDays = Math.clamp(elapsedDays, 0, DAYS_PER_WEEK);
        return BigDecimal.valueOf(boundedDays)
                .divide(BigDecimal.valueOf(DAYS_PER_WEEK), 4, RoundingMode.HALF_UP);
    }

    private static List<PlannedSession> withoutRest(List<PlannedSession> sessions) {
        return sessions.stream().filter(s -> s.getDiscipline() != Discipline.REST).toList();
    }

    private static int countByStatus(List<PlannedSession> sessions, SessionStatus status) {
        return (int) sessions.stream().filter(s -> s.getStatus() == status).count();
    }

    private static BigDecimal actualVolumeKm(List<PlannedSession> sessions, Map<UUID, Activity> activities) {
        return sessions.stream()
                .map(s -> activities.get(s.getId()))
                .filter(a -> a != null && a.getDistanceKm() != null)
                .map(Activity::getDistanceKm)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static int actualElevationM(List<PlannedSession> sessions, Map<UUID, Activity> activities) {
        return sessions.stream()
                .map(s -> activities.get(s.getId()))
                .filter(a -> a != null && a.getElevationM() != null)
                .mapToInt(Activity::getElevationM)
                .sum();
    }

    private static int actualDurationMin(List<PlannedSession> sessions, Map<UUID, Activity> activities) {
        return sessions.stream()
                .mapToInt(s -> {
                    Activity activity = activities.get(s.getId());
                    if (activity != null) {
                        return activity.getDurationMin();
                    }
                    if (s.getStatus() == SessionStatus.DONE && s.getDurationMin() != null) {
                        return s.getDurationMin();
                    }
                    return 0;
                })
                .sum();
    }
}
