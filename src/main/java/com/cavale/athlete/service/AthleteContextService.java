package com.cavale.athlete.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cavale.athlete.dto.AthleteContextResponse;
import com.cavale.athlete.dto.AthleteContextResponse.LastRace;
import com.cavale.athlete.dto.AthleteContextResponse.Season;
import com.cavale.athlete.dto.AthleteContextResponse.SessionFeedback;
import com.cavale.athlete.dto.AthleteContextResponse.Status;
import com.cavale.athlete.dto.AthleteContextResponse.UpcomingObjective;
import com.cavale.athlete.dto.AthleteContextResponse.WeekLoad;
import com.cavale.athlete.dto.AthleteHubResponse.DistanceRecord;
import com.cavale.athlete.dto.RunningStatsResponse;
import com.cavale.training.domain.Activity;
import com.cavale.training.domain.ActivityBestEffort;
import com.cavale.training.domain.Discipline;
import com.cavale.training.domain.Objective;
import com.cavale.training.domain.ObjectiveRole;
import com.cavale.training.domain.PlanWeek;
import com.cavale.training.domain.PlannedSession;
import com.cavale.training.domain.SessionStatus;
import com.cavale.training.domain.TrainingPlan;
import com.cavale.training.dto.ObjectiveResponse;
import com.cavale.training.repository.ActivityBestEffortRepository;
import com.cavale.training.repository.ActivityRepository;
import com.cavale.training.repository.ObjectiveRepository;
import com.cavale.training.repository.PlanWeekRepository;
import com.cavale.training.repository.PlannedSessionRepository;
import com.cavale.training.repository.TrainingPlanRepository;
import com.cavale.user.domain.User;
import com.cavale.user.service.UserService;

import static java.time.temporal.ChronoUnit.DAYS;

/**
 * Read model answering "where is the athlete right now?" — the context any
 * coach (the owner's Claude over MCP, or a human) must load before creating
 * or adapting a plan. Aggregates availability, season position, recent load
 * with its subjective trail (perceived effort, pain flags), the last race,
 * and what's coming.
 */
@Service
public class AthleteContextService {

    private static final int WEEKS_BACK = 6;
    private static final int FEEDBACK_LIMIT = 10;

    private final UserService userService;
    private final TrainingPlanRepository planRepository;
    private final PlanWeekRepository weekRepository;
    private final PlannedSessionRepository sessionRepository;
    private final ActivityRepository activityRepository;
    private final ActivityBestEffortRepository bestEffortRepository;
    private final ObjectiveRepository objectiveRepository;
    private final com.cavale.gym.service.GymStatsService gymStatsService;
    private final com.cavale.gym.repository.WorkoutLogRepository workoutLogRepository;
    private final RunningStatsService runningStatsService;

    public AthleteContextService(UserService userService,
                                 TrainingPlanRepository planRepository,
                                 PlanWeekRepository weekRepository,
                                 PlannedSessionRepository sessionRepository,
                                 ActivityRepository activityRepository,
                                 ActivityBestEffortRepository bestEffortRepository,
                                 ObjectiveRepository objectiveRepository,
                                 com.cavale.gym.service.GymStatsService gymStatsService,
                                 com.cavale.gym.repository.WorkoutLogRepository workoutLogRepository,
                                 RunningStatsService runningStatsService) {
        this.userService = userService;
        this.planRepository = planRepository;
        this.weekRepository = weekRepository;
        this.sessionRepository = sessionRepository;
        this.activityRepository = activityRepository;
        this.bestEffortRepository = bestEffortRepository;
        this.objectiveRepository = objectiveRepository;
        this.gymStatsService = gymStatsService;
        this.workoutLogRepository = workoutLogRepository;
        this.runningStatsService = runningStatsService;
    }

    @Transactional(readOnly = true)
    public AthleteContextResponse getContext(UUID userId) {
        return getContext(userId, LocalDate.now());
    }

    @Transactional(readOnly = true)
    public AthleteContextResponse getContext(UUID userId, LocalDate today) {
        User user = userService.getById(userId);
        List<Activity> activities = activityRepository.findByUserId(userId);
        List<Objective> objectives = objectiveRepository.findByUserId(userId);
        List<ActivityBestEffort> efforts = bestEffortRepository.findByUserId(userId);
        List<DistanceRecord> records = AthleteStatsService.records(efforts);
        RunningStatsResponse stats = runningStatsService.getStats(userId, today);

        return new AthleteContextResponse(
                profile(user, today),
                status(user, today),
                season(userId, today),
                trainingLoad(stats),
                recentWeeks(userId, activities, today),
                gymLoad(userId, today),
                recentFeedback(activities),
                lastRace(objectives, today),
                upcoming(objectives, today),
                longRunGuard(activities, today),
                aerobic(stats),
                AthleteStatsService.trailIndex(activities, today),
                records,
                AthleteStatsService.predictions(AthleteStatsService.roadRecords(efforts)));
    }

    /** The load dials, condensed from the running stats read model. */
    private static AthleteContextResponse.TrainingLoadSummary trainingLoad(RunningStatsResponse stats) {
        if (stats.form().isEmpty()) {
            return null;
        }
        var currentForm = stats.form().getLast();
        var currentWeek = stats.weeklyEffort().isEmpty() ? null : stats.weeklyEffort().getLast();
        var currentMonotony = stats.monotony().isEmpty() ? null : stats.monotony().getLast();
        return new AthleteContextResponse.TrainingLoadSummary(
                currentForm.fitness(), currentForm.fatigue(), currentForm.formScore(),
                stats.acwr().ratio(), stats.acwr().zone().name(),
                currentWeek != null ? currentWeek.effort() : 0,
                currentWeek != null ? currentWeek.bandLow() : null,
                currentWeek != null ? currentWeek.bandHigh() : null,
                currentMonotony != null ? currentMonotony.monotony() : null,
                currentMonotony != null ? currentMonotony.strain() : null,
                currentMonotony != null && currentMonotony.flagged(),
                stats.trainingStatus() != null ? stats.trainingStatus().label().name() : null);
    }

    /** The deeper aerobic signals (VO2max, critical pace, durability), condensed. */
    private static AthleteContextResponse.AerobicProfile aerobic(RunningStatsResponse stats) {
        Integer vo2max = stats.vo2maxTrend().stream()
                .map(RunningStatsResponse.Vo2maxPoint::vo2max)
                .filter(value -> value != null)
                .reduce((first, second) -> second) // the latest month with an estimate
                .orElse(null);
        Integer criticalPace = stats.criticalPace() != null
                ? stats.criticalPace().criticalPaceSecPerKm() : null;
        Double durability = stats.durability().isEmpty() ? null
                : stats.durability().getLast().decouplingPct();
        if (vo2max == null && criticalPace == null && durability == null) {
            return null;
        }
        return new AthleteContextResponse.AerobicProfile(vo2max, criticalPace, durability);
    }

    /**
     * The RUNSAFE per-run guardrail: the athlete's trailing-30-day longest run
     * bands the next long one (BJSM 2025, N=5,205). Up to 1.3× that longest is
     * normal; 1.3–2.0× is elevated (+52% hazard); beyond 2.0× is high (+128%).
     * The most recent run is classified against the longest run in the 30 days
     * BEFORE it, so a spike shows even though today's window contains it.
     * Null until there is a run to measure. ACWR stays the chronic view.
     */
    private static AthleteContextResponse.LongRunGuard longRunGuard(List<Activity> activities,
                                                                    LocalDate today) {
        Activity longest = activities.stream()
                .filter(a -> a.getDistanceKm() != null)
                .filter(a -> !a.getDate().isBefore(today.minusDays(30)) && !a.getDate().isAfter(today))
                .max(Comparator.comparing(Activity::getDistanceKm))
                .orElse(null);
        if (longest == null) {
            return null;
        }
        double longestKm = longest.getDistanceKm().doubleValue();

        Activity lastRun = activities.stream()
                .filter(a -> a.getDistanceKm() != null && !a.getDate().isAfter(today))
                .max(Comparator.comparing(Activity::getDate)
                        .thenComparing(a -> a.getDistanceKm()))
                .orElse(null);
        BigDecimal lastKm = null;
        String lastBand = null;
        if (lastRun != null) {
            LocalDate priorFrom = lastRun.getDate().minusDays(30);
            double priorMax = activities.stream()
                    .filter(a -> a.getDistanceKm() != null)
                    .filter(a -> a.getDate().isBefore(lastRun.getDate())
                            && !a.getDate().isBefore(priorFrom))
                    .mapToDouble(a -> a.getDistanceKm().doubleValue())
                    .max().orElse(0);
            lastKm = lastRun.getDistanceKm().setScale(1, RoundingMode.HALF_UP);
            lastBand = priorMax > 0 ? band(lastRun.getDistanceKm().doubleValue(), priorMax) : null;
        }

        return new AthleteContextResponse.LongRunGuard(
                longest.getDistanceKm().setScale(1, RoundingMode.HALF_UP), longest.getDate(),
                scale1(longestKm * 1.3), scale1(longestKm * 2.0), lastKm, lastBand);
    }

    /** Where a run sits against a trailing longest: NORMAL ≤ 1.3× &lt; ELEVATED ≤ 2× &lt; HIGH. */
    private static String band(double km, double trailingLongest) {
        if (km > trailingLongest * 2.0) {
            return "HIGH";
        }
        if (km > trailingLongest * 1.3) {
            return "ELEVATED";
        }
        return "NORMAL";
    }

    private static BigDecimal scale1(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP);
    }

    /** The strength side, condensed from the gym stats read model. */
    private AthleteContextResponse.GymLoad gymLoad(UUID userId, LocalDate today) {
        var stats = gymStatsService.getStats(userId, today);
        List<com.cavale.gym.domain.WorkoutLog> finished = workoutLogRepository
                .findByUserIdAndStatusOrderByStartedAtDesc(userId,
                        com.cavale.gym.domain.WorkoutStatus.FINISHED);
        List<AthleteContextResponse.GymWeek> weeks = stats.weeklyTonnage().stream()
                .skip(Math.max(0, stats.weeklyTonnage().size() - WEEKS_BACK))
                .map(w -> new AthleteContextResponse.GymWeek(w.weekStart(), w.workouts(),
                        w.tonnageKg(),
                        (int) finished.stream()
                                .filter(com.cavale.gym.domain.WorkoutLog::isPainFlag)
                                .filter(log -> {
                                    LocalDate day = LocalDate.ofInstant(log.getStartedAt(),
                                            java.time.ZoneId.systemDefault());
                                    return !day.isBefore(w.weekStart())
                                            && day.isBefore(w.weekStart().plusDays(7));
                                })
                                .count()))
                .toList();
        List<AthleteContextResponse.GymPr> prs = stats.prWall().stream()
                .limit(5)
                .map(pr -> new AthleteContextResponse.GymPr(pr.exerciseName(), pr.reps(),
                        pr.weightKg(), pr.date()))
                .toList();
        return new AthleteContextResponse.GymLoad(weeks, prs);
    }

    private static AthleteContextResponse.Profile profile(User user, LocalDate today) {
        Integer age = user.getBirthDate() != null
                ? (int) ChronoUnit.YEARS.between(user.getBirthDate(), today)
                : null;
        return new AthleteContextResponse.Profile(user.getDisplayName(), age, user.getWeightKg(),
                user.getHeightCm(), user.getMaxHr(), user.getRestingHr(),
                user.getPreferredLanguage());
    }

    private static Status status(User user, LocalDate today) {
        Long daysSince = user.getStatusSince() != null
                ? DAYS.between(user.getStatusSince(), today)
                : null;
        return new Status(user.getAthleteStatus(), user.getStatusNote(),
                user.getStatusSince(), daysSince);
    }

    /** The season the athlete is in — or the next one when between seasons. */
    private Season season(UUID userId, LocalDate today) {
        List<TrainingPlan> plans = planRepository.findByUserIdOrderByStartDateDesc(userId);
        TrainingPlan plan = plans.stream()
                .filter(p -> !p.getStartDate().isAfter(today) && !p.getEndDate().isBefore(today))
                .findFirst()
                .orElseGet(() -> plans.stream()
                        .filter(p -> p.getStartDate().isAfter(today))
                        .min(Comparator.comparing(TrainingPlan::getStartDate))
                        .orElse(null));
        if (plan == null) {
            return null;
        }

        List<PlanWeek> weeks = weekRepository.findByPlanIdOrderByWeekNumber(plan.getId());
        PlanWeek currentWeek = weeks.stream()
                .filter(w -> !w.getStartDate().isAfter(today)
                        && today.isBefore(w.getStartDate().plusDays(7)))
                .findFirst()
                .orElse(null);

        Objective main = objectiveRepository.findByPlanIdAndRole(plan.getId(), ObjectiveRole.MAIN)
                .orElse(null);
        return new Season(plan.getId(), plan.getName(), plan.getGoal(), plan.getStatus(),
                plan.getStartDate(), plan.getEndDate(),
                currentWeek != null ? currentWeek.getWeekNumber() : null, weeks.size(),
                currentWeek != null ? currentWeek.getWeekType() : null,
                currentWeek != null ? currentWeek.getPhase() : null,
                main != null ? ObjectiveResponse.from(main) : null);
    }

    /** The last {@value WEEKS_BACK} ISO weeks including the current one, oldest first. */
    private List<WeekLoad> recentWeeks(UUID userId, List<Activity> activities, LocalDate today) {
        LocalDate currentWeekStart = today.with(DayOfWeek.MONDAY);
        LocalDate from = currentWeekStart.minusWeeks(WEEKS_BACK - 1);
        List<PlannedSession> sessions = sessionRepository
                .findByUserIdAndDateBetweenOrderByDateAscOrderInDayAsc(userId, from,
                        currentWeekStart.plusDays(6));

        List<WeekLoad> weeks = new ArrayList<>();
        for (int i = WEEKS_BACK - 1; i >= 0; i--) {
            LocalDate weekStart = currentWeekStart.minusWeeks(i);
            LocalDate weekEnd = weekStart.plusDays(6);

            List<PlannedSession> planned = sessions.stream()
                    .filter(s -> !s.getDate().isBefore(weekStart) && !s.getDate().isAfter(weekEnd))
                    .filter(s -> s.getDiscipline() != Discipline.REST)
                    .toList();
            List<Activity> runs = activities.stream()
                    .filter(a -> !a.getDate().isBefore(weekStart) && !a.getDate().isAfter(weekEnd))
                    .toList();

            weeks.add(new WeekLoad(weekStart,
                    planned.size(),
                    (int) planned.stream().filter(s -> s.getStatus() == SessionStatus.DONE).count(),
                    (int) planned.stream().filter(s -> s.getStatus() == SessionStatus.SKIPPED).count(),
                    runs.size(),
                    runs.stream().map(Activity::getDistanceKm).filter(d -> d != null)
                            .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add),
                    runs.stream().mapToInt(Activity::getDurationMin).sum(),
                    runs.stream().filter(a -> a.getElevationM() != null)
                            .mapToInt(Activity::getElevationM).sum(),
                    runs.stream().filter(a -> a.getRelativeEffort() != null)
                            .mapToInt(Activity::getRelativeEffort).sum(),
                    (int) runs.stream().filter(Activity::isPainFlag).count()));
        }
        return weeks;
    }

    /** The freshest subjective feedback, newest first. */
    private static List<SessionFeedback> recentFeedback(List<Activity> activities) {
        return activities.stream()
                .filter(a -> a.getPerceivedEffort() != null || a.isPainFlag())
                .sorted(Comparator.comparing(Activity::getDate).reversed())
                .limit(FEEDBACK_LIMIT)
                .map(a -> new SessionFeedback(a.getDate(),
                        a.getSession() != null ? a.getSession().getTitle() : a.getName(),
                        a.getPerceivedEffort(), a.isPainFlag(), a.getComment()))
                .toList();
    }

    /** The most recent RACE objective already run — recovery context. */
    private static LastRace lastRace(List<Objective> objectives, LocalDate today) {
        return objectives.stream()
                .filter(o -> o.getDate() != null && o.getDate().isBefore(today))
                .filter(o -> o.getType() == com.cavale.training.domain.ObjectiveType.RACE)
                .max(Comparator.comparing(Objective::getDate))
                .map(o -> new LastRace(o.getName(), o.getDate(), DAYS.between(o.getDate(), today),
                        o.getDistanceKm(), o.getElevationGainM(), o.getResultTimeMin(),
                        o.getTargetTimeMin()))
                .orElse(null);
    }

    private static List<UpcomingObjective> upcoming(List<Objective> objectives, LocalDate today) {
        return objectives.stream()
                .filter(o -> o.getDate() != null && !o.getDate().isBefore(today))
                .sorted(Comparator.comparing(Objective::getDate))
                .map(o -> new UpcomingObjective(o.getName(), o.getDate(),
                        DAYS.between(today, o.getDate()), o.getType(), o.getRole(),
                        o.getKind(), o.getIntensity(), o.getDistanceKm(), o.getElevationGainM(),
                        o.getTargetTimeMin()))
                .toList();
    }
}
