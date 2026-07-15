package com.cavale.athlete.service;

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
import com.cavale.training.domain.Activity;
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
        List<DistanceRecord> records = AthleteStatsService
                .records(bestEffortRepository.findByUserId(userId));

        return new AthleteContextResponse(
                profile(user, today),
                status(user, today),
                season(userId, today),
                trainingLoad(userId, today),
                recentWeeks(userId, activities, today),
                gymLoad(userId, today),
                recentFeedback(activities),
                lastRace(objectives, today),
                upcoming(objectives, today),
                records,
                AthleteStatsService.predictions(records));
    }

    /** The load dials, condensed from the running stats read model. */
    private AthleteContextResponse.TrainingLoadSummary trainingLoad(UUID userId, LocalDate today) {
        var stats = runningStatsService.getStats(userId, today);
        if (stats.form().isEmpty()) {
            return null;
        }
        var currentForm = stats.form().getLast();
        var currentWeek = stats.weeklyEffort().isEmpty() ? null : stats.weeklyEffort().getLast();
        return new AthleteContextResponse.TrainingLoadSummary(
                currentForm.fitness(), currentForm.fatigue(), currentForm.formScore(),
                stats.acwr().ratio(), stats.acwr().zone().name(),
                currentWeek != null ? currentWeek.effort() : 0,
                currentWeek != null ? currentWeek.bandLow() : null,
                currentWeek != null ? currentWeek.bandHigh() : null);
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
                        o.getDistanceKm(), o.getTargetTimeMin()))
                .toList();
    }
}
