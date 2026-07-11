package com.cavale.athlete.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cavale.athlete.dto.AthleteHubResponse;
import com.cavale.athlete.dto.AthleteHubResponse.DistanceRecord;
import com.cavale.athlete.dto.AthleteHubResponse.LongestRuns;
import com.cavale.athlete.dto.AthleteHubResponse.MonthlyStat;
import com.cavale.athlete.dto.AthleteHubResponse.PeriodTotals;
import com.cavale.athlete.dto.AthleteHubResponse.Prediction;
import com.cavale.athlete.dto.AthleteHubResponse.Profile;
import com.cavale.athlete.dto.AthleteHubResponse.RunRef;
import com.cavale.athlete.dto.AthleteHubResponse.Season;
import com.cavale.athlete.dto.AthleteHubResponse.SyncState;
import com.cavale.athlete.dto.AthleteHubResponse.Timeframe;
import com.cavale.athlete.dto.AthleteHubResponse.Totals;
import com.cavale.athlete.dto.AthleteHubResponse.WeeklyEffort;
import com.cavale.integration.strava.StravaConnectionRepository;
import com.cavale.training.domain.Activity;
import com.cavale.training.domain.ActivityBestEffort;
import com.cavale.training.domain.Objective;
import com.cavale.training.domain.ObjectiveRole;
import com.cavale.training.domain.TrainingPlan;
import com.cavale.training.dto.ObjectiveResponse;
import com.cavale.training.repository.ActivityBestEffortRepository;
import com.cavale.training.repository.ActivityRepository;
import com.cavale.training.repository.ObjectiveRepository;
import com.cavale.training.repository.TrainingPlanRepository;
import com.cavale.user.domain.User;
import com.cavale.user.service.UserService;

/**
 * Read model for the athlete hub: profile, seasons (past/current/future
 * objectives), distance records, race-time estimations, and training trends
 * aggregated from the whole activity history.
 */
@Service
public class AthleteStatsService {

    /** Canonical record distances (m) → French label, in display order. */
    private static final Map<Integer, String> RECORD_DISTANCES = new LinkedHashMap<>();
    static {
        RECORD_DISTANCES.put(1000, "1 km");
        RECORD_DISTANCES.put(5000, "5 km");
        RECORD_DISTANCES.put(10000, "10 km");
        RECORD_DISTANCES.put(15000, "15 km");
        RECORD_DISTANCES.put(21097, "Semi");
        RECORD_DISTANCES.put(30000, "30 km");
        RECORD_DISTANCES.put(42195, "Marathon");
        RECORD_DISTANCES.put(50000, "50 km");
    }

    private static final int[] PREDICTION_TARGETS = {5000, 10000, 21097, 42195};

    /** Riegel endurance exponent: t2 = t1 × (d2/d1)^1.06. */
    private static final double RIEGEL_EXPONENT = 1.06;

    private static final int MONTHS_BACK = 12;
    private static final int WEEKS_BACK = 16;

    private final UserService userService;
    private final ActivityRepository activityRepository;
    private final ActivityBestEffortRepository bestEffortRepository;
    private final TrainingPlanRepository planRepository;
    private final ObjectiveRepository objectiveRepository;
    private final StravaConnectionRepository connectionRepository;

    public AthleteStatsService(UserService userService,
                               ActivityRepository activityRepository,
                               ActivityBestEffortRepository bestEffortRepository,
                               TrainingPlanRepository planRepository,
                               ObjectiveRepository objectiveRepository,
                               StravaConnectionRepository connectionRepository) {
        this.userService = userService;
        this.activityRepository = activityRepository;
        this.bestEffortRepository = bestEffortRepository;
        this.planRepository = planRepository;
        this.objectiveRepository = objectiveRepository;
        this.connectionRepository = connectionRepository;
    }

    @Transactional(readOnly = true)
    public AthleteHubResponse getHub(UUID userId) {
        return getHub(userId, LocalDate.now());
    }

    @Transactional(readOnly = true)
    public AthleteHubResponse getHub(UUID userId, LocalDate today) {
        User user = userService.getById(userId);
        List<Activity> activities = activityRepository.findByUserId(userId);
        List<DistanceRecord> records = records(bestEffortRepository.findByUserId(userId));

        return new AthleteHubResponse(
                profile(user),
                seasons(userId, today),
                records,
                longestRuns(activities),
                predictions(records),
                new Totals(
                        totals(activities.stream()
                                .filter(a -> a.getDate().getYear() == today.getYear()).toList()),
                        totals(activities)),
                monthly(activities, today),
                weeklyEffort(activities, today),
                syncState(userId));
    }

    private static Profile profile(User user) {
        return new Profile(user.getDisplayName(), user.getEmail(), user.getWeightKg(),
                user.getHeightCm(), user.getBirthDate(), user.getMaxHr(), user.getRestingHr(),
                user.getCreatedAt());
    }

    /* ── Seasons: every plan + its main objective, on a timeline ───────── */

    private List<Season> seasons(UUID userId, LocalDate today) {
        List<TrainingPlan> plans = planRepository.findByUserIdOrderByStartDateDesc(userId);
        List<Season> seasons = new ArrayList<>();
        for (TrainingPlan plan : plans) {
            Objective main = objectiveRepository.findByPlanIdAndRole(plan.getId(), ObjectiveRole.MAIN)
                    .orElse(null);
            Timeframe timeframe = plan.getEndDate().isBefore(today) ? Timeframe.PAST
                    : plan.getStartDate().isAfter(today) ? Timeframe.FUTURE
                    : Timeframe.CURRENT;
            seasons.add(new Season(plan.getId(), plan.getName(), plan.getStatus(),
                    plan.getStartDate(), plan.getEndDate(), timeframe,
                    main != null ? ObjectiveResponse.from(main) : null));
        }
        seasons.sort(Comparator.comparing(Season::startDate));
        return seasons;
    }

    /* ── Records & estimations ─────────────────────────────────────────── */

    /** Best time per canonical distance (±1 % tolerance on Strava's metres). */
    static List<DistanceRecord> records(List<ActivityBestEffort> efforts) {
        List<DistanceRecord> records = new ArrayList<>();
        for (Map.Entry<Integer, String> target : RECORD_DISTANCES.entrySet()) {
            efforts.stream()
                    .filter(e -> Math.abs(e.getDistanceM() - target.getKey()) <= target.getKey() * 0.01)
                    .min(Comparator.comparingInt(ActivityBestEffort::getElapsedSec))
                    .ifPresent(best -> records.add(new DistanceRecord(target.getValue(),
                            target.getKey(), best.getElapsedSec(), best.getDate(),
                            best.getActivity().getName())));
        }
        return records;
    }

    /**
     * Riegel estimations for the classic distances the athlete has NO record
     * at (a real record beats any estimate), each from the closest record in
     * log-distance — clearly labeled as estimations, not results.
     */
    static List<Prediction> predictions(List<DistanceRecord> records) {
        if (records.isEmpty()) {
            return List.of();
        }
        List<Prediction> predictions = new ArrayList<>();
        for (int target : PREDICTION_TARGETS) {
            boolean hasRecord = records.stream().anyMatch(r -> r.distanceM() == target);
            if (hasRecord) {
                continue;
            }
            DistanceRecord base = records.stream()
                    .min(Comparator.comparingDouble(r ->
                            Math.abs(Math.log((double) target / r.distanceM()))))
                    .orElse(null);
            if (base == null) {
                continue;
            }
            int seconds = (int) Math.round(
                    base.seconds() * Math.pow((double) target / base.distanceM(), RIEGEL_EXPONENT));
            predictions.add(new Prediction(RECORD_DISTANCES.get(target), target, seconds,
                    Math.round(seconds * 1000f / target), base.label()));
        }
        return predictions;
    }

    private static LongestRuns longestRuns(List<Activity> activities) {
        Optional<Activity> byDistance = activities.stream()
                .filter(a -> a.getDistanceKm() != null)
                .max(Comparator.comparing(Activity::getDistanceKm));
        Optional<Activity> byDuration = activities.stream()
                .max(Comparator.comparingInt(Activity::getDurationMin));
        return new LongestRuns(byDistance.map(AthleteStatsService::runRef).orElse(null),
                byDuration.map(AthleteStatsService::runRef).orElse(null));
    }

    private static RunRef runRef(Activity activity) {
        return new RunRef(activity.getDate(), activity.getName(),
                activity.getDistanceKm(), activity.getDurationMin());
    }

    /* ── Trends ────────────────────────────────────────────────────────── */

    private static PeriodTotals totals(List<Activity> activities) {
        BigDecimal distance = activities.stream()
                .map(Activity::getDistanceKm)
                .filter(d -> d != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int duration = activities.stream().mapToInt(Activity::getDurationMin).sum();
        int elevation = activities.stream()
                .filter(a -> a.getElevationM() != null)
                .mapToInt(Activity::getElevationM).sum();
        return new PeriodTotals(activities.size(), distance.setScale(1, RoundingMode.HALF_UP),
                duration, elevation);
    }

    /** The last 12 months, oldest first, empty months included. */
    static List<MonthlyStat> monthly(List<Activity> activities, LocalDate today) {
        Map<YearMonth, List<Activity>> byMonth = new LinkedHashMap<>();
        YearMonth current = YearMonth.from(today);
        for (int i = MONTHS_BACK - 1; i >= 0; i--) {
            byMonth.put(current.minusMonths(i), new ArrayList<>());
        }
        for (Activity activity : activities) {
            List<Activity> bucket = byMonth.get(YearMonth.from(activity.getDate()));
            if (bucket != null) {
                bucket.add(activity);
            }
        }

        List<MonthlyStat> stats = new ArrayList<>();
        for (Map.Entry<YearMonth, List<Activity>> entry : byMonth.entrySet()) {
            List<Activity> runs = entry.getValue();
            PeriodTotals totals = totals(runs);
            stats.add(new MonthlyStat(entry.getKey().toString(), totals.runs(),
                    totals.distanceKm(), totals.durationMin(), totals.elevationM(),
                    avgPaceSecPerKm(runs), weightedAvgHr(runs), weightedAvgCadence(runs),
                    relativeEffortSum(runs)));
        }
        return stats;
    }

    /** The last 16 ISO weeks (Monday start), oldest first, empty weeks included. */
    static List<WeeklyEffort> weeklyEffort(List<Activity> activities, LocalDate today) {
        LocalDate currentWeekStart = today.with(DayOfWeek.MONDAY);
        Map<LocalDate, List<Activity>> byWeek = new LinkedHashMap<>();
        for (int i = WEEKS_BACK - 1; i >= 0; i--) {
            byWeek.put(currentWeekStart.minusWeeks(i), new ArrayList<>());
        }
        for (Activity activity : activities) {
            List<Activity> bucket = byWeek.get(activity.getDate().with(DayOfWeek.MONDAY));
            if (bucket != null) {
                bucket.add(activity);
            }
        }
        return byWeek.entrySet().stream()
                .map(entry -> new WeeklyEffort(entry.getKey(),
                        relativeEffortSum(entry.getValue()),
                        totals(entry.getValue()).distanceKm()))
                .toList();
    }

    /** Weighted pace: total time over total distance (sec/km). */
    private static Integer avgPaceSecPerKm(List<Activity> activities) {
        double km = 0;
        long seconds = 0;
        for (Activity activity : activities) {
            if (activity.getDistanceKm() != null && activity.getDistanceKm().signum() > 0) {
                km += activity.getDistanceKm().doubleValue();
                seconds += activity.getDurationMin() * 60L;
            }
        }
        return km > 0 ? (int) Math.round(seconds / km) : null;
    }

    private static Integer weightedAvgHr(List<Activity> activities) {
        long weighted = 0;
        long minutes = 0;
        for (Activity activity : activities) {
            if (activity.getAvgHr() != null) {
                weighted += (long) activity.getAvgHr() * activity.getDurationMin();
                minutes += activity.getDurationMin();
            }
        }
        return minutes > 0 ? (int) Math.round((double) weighted / minutes) : null;
    }

    private static BigDecimal weightedAvgCadence(List<Activity> activities) {
        double weighted = 0;
        long minutes = 0;
        for (Activity activity : activities) {
            if (activity.getAvgCadenceSpm() != null) {
                weighted += activity.getAvgCadenceSpm().doubleValue() * activity.getDurationMin();
                minutes += activity.getDurationMin();
            }
        }
        return minutes > 0
                ? BigDecimal.valueOf(weighted / minutes).setScale(1, RoundingMode.HALF_UP)
                : null;
    }

    private static int relativeEffortSum(List<Activity> activities) {
        return activities.stream()
                .filter(a -> a.getRelativeEffort() != null)
                .mapToInt(Activity::getRelativeEffort).sum();
    }

    private SyncState syncState(UUID userId) {
        return new SyncState(
                connectionRepository.findByUserId(userId).isPresent(),
                activityRepository.countByUserIdAndExternalIdIsNotNull(userId),
                activityRepository.countByUserIdAndExternalIdIsNotNullAndRecordsAnalyzedFalse(userId));
    }
}
