package com.cavale.athlete.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

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
import com.cavale.athlete.dto.AthleteHubResponse.TrailIndex;
import com.cavale.athlete.dto.AthleteHubResponse.WeeklyEffort;
import com.cavale.athlete.dto.AthleteHubResponse.WeeklyStat;
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

    /** A run climbing less than this (m of D+ per km) is road-like — the base
     *  the road predictors use, so trail D+ stops deflating them. */
    private static final int ROAD_DPLUS_PER_KM_MAX = 20;

    /* ── Personal trail performance index (P7) ─────────────────────────── */
    private static final int TRAIL_INDEX_MONTHS = 36;
    private static final int TRAIL_INDEX_BEST_N = 5;
    private static final int TRAIL_INDEX_MIN_EFFORTS = 3;
    /** A run climbing this much (m of D+ per km) or more counts as trail. */
    private static final int TRAIL_DPLUS_PER_KM = 25;
    private static final double TRAIL_INDEX_MIN_KM = 8;
    private static final int TRAIL_INDEX_MIN_MIN = 45;
    /** Recency half-weight horizon: an effort N months old weighs e^(-N/12). */
    private static final double TRAIL_INDEX_RECENCY_MONTHS = 12.0;

    /** Two years of monthly buckets — the widest zoom of the hub trends. */
    private static final int MONTHS_BACK = 24;
    /** Six months of weekly buckets — the fine-grained zoom of the hub trends. */
    private static final int WEEKS_STATS_BACK = 26;
    private static final int WEEKS_BACK = 16;

    private final UserService userService;
    private final ActivityRepository activityRepository;
    private final ActivityBestEffortRepository bestEffortRepository;
    private final TrainingPlanRepository planRepository;
    private final ObjectiveRepository objectiveRepository;
    private final StravaConnectionRepository connectionRepository;
    private final com.cavale.gym.repository.WorkoutLogRepository workoutLogRepository;

    public AthleteStatsService(UserService userService,
                               ActivityRepository activityRepository,
                               ActivityBestEffortRepository bestEffortRepository,
                               TrainingPlanRepository planRepository,
                               ObjectiveRepository objectiveRepository,
                               StravaConnectionRepository connectionRepository,
                               com.cavale.gym.repository.WorkoutLogRepository workoutLogRepository) {
        this.userService = userService;
        this.activityRepository = activityRepository;
        this.bestEffortRepository = bestEffortRepository;
        this.planRepository = planRepository;
        this.objectiveRepository = objectiveRepository;
        this.connectionRepository = connectionRepository;
        this.workoutLogRepository = workoutLogRepository;
    }

    @Transactional(readOnly = true)
    public AthleteHubResponse getHub(UUID userId) {
        return getHub(userId, LocalDate.now());
    }

    @Transactional(readOnly = true)
    public AthleteHubResponse getHub(UUID userId, LocalDate today) {
        User user = userService.getById(userId);
        // The hub is the running dashboard — cross-training bikes stay out of it.
        List<Activity> activities = activityRepository.findByUserId(userId).stream()
                .filter(Activity::isRun).toList();
        List<ActivityBestEffort> efforts = bestEffortRepository.findByUserId(userId);
        List<DistanceRecord> records = records(efforts);

        return new AthleteHubResponse(
                profile(user),
                seasons(userId, today),
                records,
                longestRuns(activities),
                predictions(roadRecords(efforts)),
                trailIndex(activities, today),
                new Totals(
                        totals(activities.stream()
                                .filter(a -> a.getDate().getYear() == today.getYear()).toList()),
                        totals(activities)),
                monthly(activities, today),
                weekly(activities, gymMinutesByWeek(userId), today),
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
        return records(efforts, effort -> true);
    }

    /**
     * Records restricted to road-like efforts — the base the road predictors
     * (Riegel / Cameron / Vickers-Vertosick) must use. A best-effort split run
     * on a hilly trail understates road ability, so a split whose PARENT run
     * climbs {@value #ROAD_DPLUS_PER_KM_MAX} m or more per km is excluded (we
     * only have the whole run's D+/km, not the split's). The trail time
     * estimates keep their own hilly-long-run set and are unaffected.
     */
    static List<DistanceRecord> roadRecords(List<ActivityBestEffort> efforts) {
        return records(efforts, AthleteStatsService::isRoadLike);
    }

    private static List<DistanceRecord> records(List<ActivityBestEffort> efforts,
                                                Predicate<ActivityBestEffort> filter) {
        List<DistanceRecord> records = new ArrayList<>();
        for (Map.Entry<Integer, String> target : RECORD_DISTANCES.entrySet()) {
            efforts.stream()
                    .filter(filter)
                    .filter(e -> Math.abs(e.getDistanceM() - target.getKey()) <= target.getKey() * 0.01)
                    .min(Comparator.comparingInt(ActivityBestEffort::getElapsedSec))
                    .ifPresent(best -> records.add(new DistanceRecord(target.getValue(),
                            target.getKey(), best.getElapsedSec(), best.getDate(),
                            best.getActivity().getName())));
        }
        return records;
    }

    /** A split counts as road-like when its parent run stays under the trail D+/km. */
    public static boolean isRoadLike(ActivityBestEffort effort) {
        Activity activity = effort.getActivity();
        BigDecimal km = activity.getDistanceKm();
        if (km == null || km.signum() <= 0) {
            return true; // terrain unknown → keep the record rather than drop it
        }
        int elevation = activity.getElevationM() != null ? activity.getElevationM() : 0;
        return elevation / km.doubleValue() < ROAD_DPLUS_PER_KM_MAX;
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

    /**
     * The personal trail performance index (P7). Scores every substantial
     * trail effort of the last {@value #TRAIL_INDEX_MONTHS} months by
     * <em>km-effort per hour × √(km-effort)</em> — rewarding both covering the
     * km-effort scale fast (fitness) and taking on bigger mountain efforts
     * (endurance) — then takes a recency-weighted mean of the best
     * {@value #TRAIL_INDEX_BEST_N}, so recent form leads without erasing
     * history. One number the athlete watches climb. Null under
     * {@value #TRAIL_INDEX_MIN_EFFORTS} qualifying efforts.
     */
    static TrailIndex trailIndex(List<Activity> activities, LocalDate today) {
        LocalDate from = today.minusMonths(TRAIL_INDEX_MONTHS);
        record Scored(Activity activity, double kmEffort, double score) {
        }
        List<Scored> scored = activities.stream()
                .filter(a -> a.getDistanceKm() != null && a.getElevationM() != null)
                .filter(a -> !a.getDate().isBefore(from) && !a.getDate().isAfter(today))
                .filter(a -> a.getDistanceKm().doubleValue() >= TRAIL_INDEX_MIN_KM)
                .filter(a -> a.getDurationMin() >= TRAIL_INDEX_MIN_MIN)
                .filter(a -> a.getElevationM() / a.getDistanceKm().doubleValue() >= TRAIL_DPLUS_PER_KM)
                .map(a -> {
                    double kmEffort = a.getDistanceKm().doubleValue() + a.getElevationM() / 100.0;
                    double kmEffortPerHour = kmEffort / (a.getDurationMin() / 60.0);
                    return new Scored(a, kmEffort, kmEffortPerHour * Math.sqrt(kmEffort));
                })
                .sorted(Comparator.comparingDouble(Scored::score).reversed())
                .limit(TRAIL_INDEX_BEST_N)
                .toList();
        if (scored.size() < TRAIL_INDEX_MIN_EFFORTS) {
            return null;
        }

        double weighted = 0;
        double weightSum = 0;
        for (Scored s : scored) {
            long monthsAgo = ChronoUnit.MONTHS.between(YearMonth.from(s.activity().getDate()),
                    YearMonth.from(today));
            double weight = Math.exp(-monthsAgo / TRAIL_INDEX_RECENCY_MONTHS);
            weighted += s.score() * weight;
            weightSum += weight;
        }
        Scored best = scored.getFirst();
        return new TrailIndex((int) Math.round(weighted / weightSum), scored.size(),
                best.activity().getName(), best.activity().getDate(),
                BigDecimal.valueOf(best.kmEffort()).setScale(0, RoundingMode.HALF_UP));
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

    /** The last 24 months, oldest first, empty months included. */
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

    /** Real strength minutes per ISO week (Monday start), from finished workout logs. */
    private Map<LocalDate, Integer> gymMinutesByWeek(UUID userId) {
        Map<LocalDate, Integer> byWeek = new java.util.HashMap<>();
        for (var log : workoutLogRepository.findByUserIdAndStatusOrderByStartedAtDesc(
                userId, com.cavale.gym.domain.WorkoutStatus.FINISHED)) {
            if (log.getDurationMin() == null) {
                continue;
            }
            LocalDate weekStart = LocalDate.ofInstant(log.getStartedAt(), com.cavale.common.AppTime.ZONE)
                    .with(DayOfWeek.MONDAY);
            byWeek.merge(weekStart, log.getDurationMin(), Integer::sum);
        }
        return byWeek;
    }

    /** The last 26 ISO weeks (Monday start), oldest first, empty weeks included. */
    static List<WeeklyStat> weekly(List<Activity> activities, Map<LocalDate, Integer> gymMinutes,
                                   LocalDate today) {
        LocalDate currentWeekStart = today.with(DayOfWeek.MONDAY);
        Map<LocalDate, List<Activity>> byWeek = new LinkedHashMap<>();
        for (int i = WEEKS_STATS_BACK - 1; i >= 0; i--) {
            byWeek.put(currentWeekStart.minusWeeks(i), new ArrayList<>());
        }
        for (Activity activity : activities) {
            List<Activity> bucket = byWeek.get(activity.getDate().with(DayOfWeek.MONDAY));
            if (bucket != null) {
                bucket.add(activity);
            }
        }

        List<WeeklyStat> stats = new ArrayList<>();
        for (Map.Entry<LocalDate, List<Activity>> entry : byWeek.entrySet()) {
            List<Activity> runs = entry.getValue();
            PeriodTotals totals = totals(runs);
            stats.add(new WeeklyStat(entry.getKey(), totals.runs(),
                    totals.distanceKm(), totals.durationMin(),
                    gymMinutes.getOrDefault(entry.getKey(), 0), totals.elevationM(),
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
