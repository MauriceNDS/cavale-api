package com.cavale.athlete.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cavale.athlete.dto.AthleteHubResponse.DistanceRecord;
import com.cavale.athlete.dto.RunningStatsResponse;
import com.cavale.athlete.dto.RunningStatsResponse.Acwr;
import com.cavale.athlete.dto.RunningStatsResponse.AcwrZone;
import com.cavale.athlete.dto.RunningStatsResponse.DayForm;
import com.cavale.athlete.dto.RunningStatsResponse.DurationCheckpoint;
import com.cavale.athlete.dto.RunningStatsResponse.MonthEfficiency;
import com.cavale.athlete.dto.RunningStatsResponse.RoadPrediction;
import com.cavale.athlete.dto.RunningStatsResponse.TrailEstimate;
import com.cavale.athlete.dto.RunningStatsResponse.TrainingStatus;
import com.cavale.athlete.dto.RunningStatsResponse.TrainingStatusLabel;
import com.cavale.athlete.dto.RunningStatsResponse.CriticalPace;
import com.cavale.athlete.dto.RunningStatsResponse.DurabilityPoint;
import com.cavale.athlete.dto.RunningStatsResponse.Vo2maxPoint;
import com.cavale.athlete.dto.RunningStatsResponse.WeekEffort;
import com.cavale.athlete.dto.RunningStatsResponse.WeekMonotony;
import com.cavale.athlete.dto.RunningStatsResponse.WeekVolume;
import com.cavale.training.domain.Activity;
import com.cavale.training.domain.ActivityBestEffort;
import com.cavale.training.domain.Objective;
import com.cavale.training.repository.ActivityBestEffortRepository;
import com.cavale.training.repository.ActivityRepository;
import com.cavale.training.repository.ObjectiveRepository;
import com.cavale.user.domain.User;
import com.cavale.user.service.UserService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Deep running statistics, computed from the activity corpus.
 *
 * Training load follows the Banister impulse-response model Strava's
 * Fitness &amp; Freshness popularised: fitness is a 42-day exponentially
 * weighted average of daily relative effort, fatigue a 7-day one, form
 * their difference. Runs without a suffer score get a duration-based
 * estimate so the curves stay continuous (flagged as estimated).
 *
 * Race predictions deliberately return RANGES across models (Riegel,
 * Cameron, a Vickers-Vertosick-style mileage-adjusted exponent) — one
 * number would be a lie. Trail objectives are timed from the athlete's
 * OWN median pace per km-effort (ITRA: km + D+/100) with a Riegel-style
 * fatigue term on the km-effort ratio.
 */
@Service
public class RunningStatsService {

    static final int FORM_DAYS = 365;
    private static final int WEEKS = 52;
    private static final int EFFICIENCY_MONTHS = 12;
    /** Foster monotony at or above this flags illness / overtraining risk. */
    static final double MONOTONY_FLAG = 2.0;
    /** Window the training-status verdict measures the fitness trend over. */
    static final int FITNESS_LOOKBACK_DAYS = 28;
    private static final double FITNESS_TAU = 42.0;
    private static final double FATIGUE_TAU = 7.0;
    /** Duration-based RE estimate for HR-less runs: ~0.7 point per minute. */
    private static final double ESTIMATED_RE_PER_MIN = 0.7;
    private static final int[] CHECKPOINTS_MIN = {30, 60, 90, 120, 180};
    private static final double RIEGEL_K = 1.06;
    /** Trail scaling exponent on km-effort — the ultra-fatigue term. */
    private static final double TRAIL_K = 1.07;
    /** Runs steeper than this (m of D+ per km) count as trail for pacing. */
    private static final int TRAIL_DPLUS_PER_KM = 25;
    /** A run climbing less than this (m of D+ per km) is flat enough for
     *  Daniels' VO2 cost to hold, so grade doesn't distort the estimate. */
    private static final int ROAD_DPLUS_PER_KM_MAX = 20;
    private static final int VO2MAX_MONTHS = 12;
    private static final int VO2MAX_MIN_MIN = 20;           // a steady effort, not a sprint
    private static final double VO2MAX_MIN_INTENSITY = 0.5; // ignore easy recovery shuffles
    /** VO2 at rest (ml/kg/min) — the floor the VO2-reserve scaling builds from. */
    private static final double VO2_REST = 3.5;
    private static final int CRITICAL_PACE_MIN_POINTS = 3;
    private static final int DURABILITY_MONTHS = 12;
    private static final int DURABILITY_MIN_MIN = 90;       // long runs only

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private record RoadTarget(String label, int distanceM) {
    }

    private static final List<RoadTarget> ROAD_TARGETS = List.of(
            new RoadTarget("5 km", 5000),
            new RoadTarget("10 km", 10000),
            new RoadTarget("Semi", 21097),
            new RoadTarget("Marathon", 42195));

    private final ActivityRepository activityRepository;
    private final ActivityBestEffortRepository bestEffortRepository;
    private final ObjectiveRepository objectiveRepository;
    private final UserService userService;

    public RunningStatsService(ActivityRepository activityRepository,
                               ActivityBestEffortRepository bestEffortRepository,
                               ObjectiveRepository objectiveRepository,
                               UserService userService) {
        this.activityRepository = activityRepository;
        this.bestEffortRepository = bestEffortRepository;
        this.objectiveRepository = objectiveRepository;
        this.userService = userService;
    }

    /** Series window lengths — the defaults, or stretched back to the first activity. */
    private record Windows(int formDays, int weeks, int months) {

        static final Windows DEFAULT = new Windows(FORM_DAYS, WEEKS, EFFICIENCY_MONTHS);

        static Windows covering(LocalDate first, LocalDate today) {
            int days = (int) java.time.temporal.ChronoUnit.DAYS.between(first, today) + 1;
            int weeks = (int) java.time.temporal.ChronoUnit.WEEKS.between(
                    first.with(DayOfWeek.MONDAY), today.with(DayOfWeek.MONDAY)) + 1;
            int months = (int) java.time.temporal.ChronoUnit.MONTHS.between(
                    YearMonth.from(first), YearMonth.from(today)) + 1;
            return new Windows(Math.max(FORM_DAYS, days), Math.max(WEEKS, weeks),
                    Math.max(EFFICIENCY_MONTHS, months));
        }
    }

    @Transactional(readOnly = true)
    public RunningStatsResponse getStats(UUID userId) {
        return getStats(userId, LocalDate.now(), null);
    }

    @Transactional(readOnly = true)
    public RunningStatsResponse getStats(UUID userId, LocalDate today) {
        return getStats(userId, today, null);
    }

    /** @param months series depth in months; null = the 12-month default, 0 = all-time. */
    @Transactional(readOnly = true)
    public RunningStatsResponse getStats(UUID userId, LocalDate today, Integer months) {
        // The load curves count every activity (a bike ride is real fatigue);
        // the run-only metrics — volume, pace, predictions — see runs alone.
        List<Activity> all = activityRepository.findByUserId(userId).stream()
                .sorted(Comparator.comparing(Activity::getDate))
                .toList();
        List<Activity> runs = all.stream().filter(Activity::isRun).toList();
        List<ActivityBestEffort> efforts = bestEffortRepository.findByUserId(userId);
        List<Objective> objectives = objectiveRepository.findByUserId(userId);
        User user = userService.getById(userId);

        Windows windows = months != null && months == 0 && !all.isEmpty()
                ? Windows.covering(all.get(0).getDate(), today)
                : Windows.DEFAULT;

        double weeklyKm = recentWeeklyKm(runs, today);
        List<DayForm> form = form(all, today, windows.formDays());
        Acwr acwr = acwr(all, today);
        return new RunningStatsResponse(
                form,
                weeklyEffort(all, today, windows.weeks()),
                acwr,
                weeklyVolume(runs, today, windows.weeks()),
                efficiency(runs, today, windows.months()),
                checkpoints(runs),
                roadPredictions(AthleteStatsService.roadRecords(efforts), weeklyKm),
                trailEstimates(runs, objectives, today),
                monotony(all, today, windows.weeks()),
                trainingStatus(form, acwr),
                vo2maxTrend(runs, user, today, windows.months()),
                criticalPace(efforts),
                durability(runs, today, windows.months()));
    }

    /* ── Training load (Banister / Strava F&F) ─────────────────────────── */

    /** Daily relative effort; HR-less runs get a duration-based estimate. */
    static int dailyEffort(Activity activity) {
        return activity.getRelativeEffort() != null
                ? activity.getRelativeEffort()
                : (int) Math.round(activity.getDurationMin() * ESTIMATED_RE_PER_MIN);
    }

    private static List<DayForm> form(List<Activity> activities, LocalDate today, int formDays) {
        // warm up the averages well before the visible window
        LocalDate warmupStart = today.minusDays(formDays + 3L * (long) FITNESS_TAU);
        Map<LocalDate, Integer> effortByDay = new LinkedHashMap<>();
        for (Activity activity : activities) {
            if (!activity.getDate().isBefore(warmupStart)) {
                effortByDay.merge(activity.getDate(), dailyEffort(activity), Integer::sum);
            }
        }

        double fitnessDecay = Math.exp(-1 / FITNESS_TAU);
        double fatigueDecay = Math.exp(-1 / FATIGUE_TAU);
        double fitness = 0;
        double fatigue = 0;
        List<DayForm> series = new ArrayList<>(formDays);
        LocalDate windowStart = today.minusDays(formDays - 1L);
        for (LocalDate day = warmupStart; !day.isAfter(today); day = day.plusDays(1)) {
            int effort = effortByDay.getOrDefault(day, 0);
            fitness = fitness * fitnessDecay + effort * (1 - fitnessDecay);
            fatigue = fatigue * fatigueDecay + effort * (1 - fatigueDecay);
            if (!day.isBefore(windowStart)) {
                series.add(new DayForm(day, round1(fitness), round1(fatigue),
                        round1(fitness - fatigue)));
            }
        }
        return series;
    }

    /** Weekly effort with its target band: 0.8–1.3 × the trailing 3-week average. */
    private static List<WeekEffort> weeklyEffort(List<Activity> activities, LocalDate today,
                                                 int weekCount) {
        LocalDate currentWeekStart = today.with(DayOfWeek.MONDAY);
        Map<LocalDate, int[]> weeks = new LinkedHashMap<>(); // [effort, estimatedCount]
        for (int i = weekCount + 2; i >= 0; i--) { // +3 weeks of history for the first band
            weeks.put(currentWeekStart.minusWeeks(i), new int[2]);
        }
        for (Activity activity : activities) {
            int[] bucket = weeks.get(activity.getDate().with(DayOfWeek.MONDAY));
            if (bucket != null) {
                bucket[0] += dailyEffort(activity);
                if (activity.getRelativeEffort() == null) {
                    bucket[1]++;
                }
            }
        }

        List<Map.Entry<LocalDate, int[]>> entries = List.copyOf(weeks.entrySet());
        List<WeekEffort> series = new ArrayList<>(weekCount);
        for (int i = 3; i < entries.size(); i++) {
            int previous3 = entries.get(i - 1).getValue()[0] + entries.get(i - 2).getValue()[0]
                    + entries.get(i - 3).getValue()[0];
            Integer bandLow = previous3 > 0 ? Math.round(previous3 / 3f * 0.8f) : null;
            Integer bandHigh = previous3 > 0 ? Math.round(previous3 / 3f * 1.3f) : null;
            series.add(new WeekEffort(entries.get(i).getKey(), entries.get(i).getValue()[0],
                    bandLow, bandHigh, entries.get(i).getValue()[1] > 0));
        }
        return series;
    }

    /** Acute (7 d) over chronic (28 d, weekly-averaged) load. */
    private static Acwr acwr(List<Activity> activities, LocalDate today) {
        int acute = 0;
        int chronic = 0;
        for (Activity activity : activities) {
            long daysAgo = java.time.temporal.ChronoUnit.DAYS.between(activity.getDate(), today);
            if (daysAgo < 0 || daysAgo >= 28) {
                continue;
            }
            int effort = dailyEffort(activity);
            chronic += effort;
            if (daysAgo < 7) {
                acute += effort;
            }
        }
        double chronicWeekly = chronic / 4.0;
        double ratio = chronicWeekly > 0 ? acute / chronicWeekly : 0;
        AcwrZone zone = ratio > 1.5 ? AcwrZone.DANGER
                : ratio > 1.3 ? AcwrZone.CAUTION
                : ratio >= 0.8 ? AcwrZone.OPTIMAL
                : AcwrZone.UNDER;
        return new Acwr(Math.round(ratio * 100) / 100.0, acute, (int) Math.round(chronicWeekly), zone);
    }

    /* ── Load distribution & training-status verdict ───────────────────── */

    /**
     * Foster's monotony &amp; strain over the {@value #WEEKS} ISO weeks.
     * Monotony is the mean of a week's seven daily loads over their
     * (population) standard deviation: a sky-high value means every day
     * looked the same, the pattern that precedes illness and overtraining —
     * flagged at {@value #MONOTONY_FLAG}. Strain is the week's total load
     * times its monotony. Rest days count as zero, so real hard/easy contrast
     * scores low (healthy); both are null when a week has no training, or no
     * day-to-day variance at all (standard deviation zero).
     */
    private static List<WeekMonotony> monotony(List<Activity> activities, LocalDate today,
                                               int weekCount) {
        Map<LocalDate, Integer> effortByDay = new HashMap<>();
        for (Activity activity : activities) {
            effortByDay.merge(activity.getDate(), dailyEffort(activity), Integer::sum);
        }
        LocalDate currentWeekStart = today.with(DayOfWeek.MONDAY);
        List<WeekMonotony> series = new ArrayList<>(weekCount);
        for (int i = weekCount - 1; i >= 0; i--) {
            LocalDate weekStart = currentWeekStart.minusWeeks(i);
            int total = 0;
            double[] daily = new double[7];
            for (int d = 0; d < 7; d++) {
                int load = effortByDay.getOrDefault(weekStart.plusDays(d), 0);
                daily[d] = load;
                total += load;
            }
            double mean = total / 7.0;
            double variance = 0;
            for (double load : daily) {
                variance += (load - mean) * (load - mean);
            }
            double sd = Math.sqrt(variance / 7);
            if (total == 0 || sd == 0) {
                series.add(new WeekMonotony(weekStart, null, null, false));
                continue;
            }
            double monotony = Math.round(mean / sd * 100) / 100.0;
            series.add(new WeekMonotony(weekStart, monotony, (int) Math.round(total * monotony),
                    monotony >= MONOTONY_FLAG));
        }
        return series;
    }

    /**
     * The single fused verdict, from three already-computed dials — the
     * {@value #FITNESS_LOOKBACK_DAYS}-day fitness trend, the current form
     * (fitness − fatigue) and the ACWR — collapsed to one label by a
     * documented, deterministic ladder (first match wins):
     *
     * <ol>
     *   <li>OVERREACHING — ACWR ≥ 1.5, or ≥ 1.3 with already-negative form:
     *       acute load is outrunning the base.</li>
     *   <li>RECOVERY — under-loading (ACWR &lt; 0.8) while fresh (form ≥ 0):
     *       a deliberate lighter / taper stretch.</li>
     *   <li>DETRAINING — under-loading with fitness falling more than 3 %:
     *       the base is eroding.</li>
     *   <li>PRODUCTIVE — fitness rising more than 3 %: building safely.</li>
     *   <li>MAINTAINING — everything else: steady state.</li>
     * </ol>
     */
    static TrainingStatus trainingStatus(List<DayForm> form, Acwr acwr) {
        if (form.isEmpty()) {
            return null;
        }
        DayForm current = form.getLast();
        if (current.fitness() < 1 && acwr.ratio() == 0) {
            return null; // no measurable load history yet — nothing to verdict
        }
        DayForm past = form.size() > FITNESS_LOOKBACK_DAYS
                ? form.get(form.size() - 1 - FITNESS_LOOKBACK_DAYS)
                : form.getFirst();
        double trendPct = past.fitness() > 1
                ? Math.round((current.fitness() - past.fitness()) / past.fitness() * 1000) / 10.0
                : 0;
        double formScore = current.formScore();
        double ratio = acwr.ratio();

        TrainingStatusLabel label;
        if (ratio >= 1.5 || (ratio >= 1.3 && formScore < 0)) {
            label = TrainingStatusLabel.OVERREACHING;
        } else if (ratio < 0.8 && formScore >= 0) {
            label = TrainingStatusLabel.RECOVERY;
        } else if (ratio < 0.8 && trendPct < -3) {
            label = TrainingStatusLabel.DETRAINING;
        } else if (trendPct > 3) {
            label = TrainingStatusLabel.PRODUCTIVE;
        } else {
            label = TrainingStatusLabel.MAINTAINING;
        }
        return new TrainingStatus(label, trendPct, round1(formScore), ratio);
    }

    /* ── Effective VO2max & critical pace (P5) ─────────────────────────── */

    /**
     * A {@value #VO2MAX_MONTHS}-month effective-VO2max trend. For each road-like
     * run with heart rate, Daniels' oxygen cost at the run's average speed is
     * scaled up by the fraction of heart-rate reserve it used — %HRR ≈ %VO2R,
     * the standard ACSM relationship — into an estimated VO2max; each month
     * reports the median of its runs. Needs the athlete's max HR (resting HR
     * sharpens it); without max HR the trend is empty.
     */
    private static List<Vo2maxPoint> vo2maxTrend(List<Activity> activities, User user,
                                                 LocalDate today, int monthCount) {
        Integer maxHr = user != null ? user.getMaxHr() : null;
        if (maxHr == null || maxHr <= 0) {
            return List.of();
        }
        Integer restingHr = user.getRestingHr();
        Map<YearMonth, List<Double>> byMonth = new LinkedHashMap<>();
        YearMonth current = YearMonth.from(today);
        for (int i = monthCount - 1; i >= 0; i--) {
            byMonth.put(current.minusMonths(i), new ArrayList<>());
        }
        for (Activity activity : activities) {
            Double estimate = effectiveVo2max(activity, maxHr, restingHr);
            if (estimate == null) {
                continue;
            }
            List<Double> bucket = byMonth.get(YearMonth.from(activity.getDate()));
            if (bucket != null) {
                bucket.add(estimate);
            }
        }
        return byMonth.entrySet().stream()
                .map(entry -> entry.getValue().isEmpty()
                        ? new Vo2maxPoint(entry.getKey().toString(), null, 0)
                        : new Vo2maxPoint(entry.getKey().toString(),
                                (int) Math.round(median(entry.getValue())), entry.getValue().size()))
                .toList();
    }

    /** Estimated VO2max from one steady road-like run's speed and HR, or null. */
    static Double effectiveVo2max(Activity activity, int maxHr, Integer restingHr) {
        if (activity.getAvgHr() == null || activity.getAvgHr() <= 0 || activity.getDistanceKm() == null
                || activity.getDistanceKm().signum() <= 0 || activity.getDurationMin() < VO2MAX_MIN_MIN) {
            return null;
        }
        if (activity.getElevationM() != null
                && activity.getElevationM() / activity.getDistanceKm().doubleValue() >= ROAD_DPLUS_PER_KM_MAX) {
            return null; // too hilly for a flat-ground VO2 cost
        }
        double vMetersPerMin = activity.getDistanceKm().doubleValue() * 1000 / activity.getDurationMin();
        // Daniels' running oxygen cost (ml/kg/min) at velocity v (m/min)
        double vo2 = -4.60 + 0.182258 * vMetersPerMin + 0.000104 * vMetersPerMin * vMetersPerMin;
        boolean hasReserve = restingHr != null && restingHr > 0 && maxHr > restingHr;
        double fraction = hasReserve
                ? (activity.getAvgHr() - (double) restingHr) / (maxHr - restingHr) // %HRR ≈ %VO2R
                : (double) activity.getAvgHr() / maxHr;                            // fallback %HRmax
        if (fraction < VO2MAX_MIN_INTENSITY || fraction > 1.0) {
            return null;
        }
        double vo2max = hasReserve
                ? VO2_REST + (vo2 - VO2_REST) / fraction // VO2-reserve scaling from the resting floor
                : vo2 / fraction;                        // %HRmax ≈ %VO2max
        return vo2max > 0 && vo2max < 100 ? vo2max : null;
    }

    /**
     * Critical speed from the best-effort curve. Fits distance = CS·t + D' by
     * least squares over the fastest road-like effort at each distance: the
     * slope is the critical speed (the highest sustainable pace), the intercept
     * the anaerobic distance reserve D'. Needs at least
     * {@value #CRITICAL_PACE_MIN_POINTS} distinct distances; null otherwise.
     */
    private static CriticalPace criticalPace(List<ActivityBestEffort> efforts) {
        Map<Integer, Integer> bestByDistance = new HashMap<>();
        for (ActivityBestEffort effort : efforts) {
            if (AthleteStatsService.isRoadLike(effort)) {
                bestByDistance.merge(effort.getDistanceM(), effort.getElapsedSec(), Math::min);
            }
        }
        if (bestByDistance.size() < CRITICAL_PACE_MIN_POINTS) {
            return null;
        }
        int n = bestByDistance.size();
        double sumT = 0, sumD = 0, sumTT = 0, sumTD = 0;
        for (Map.Entry<Integer, Integer> entry : bestByDistance.entrySet()) {
            double t = entry.getValue();
            double d = entry.getKey();
            sumT += t;
            sumD += d;
            sumTT += t * t;
            sumTD += t * d;
        }
        double denom = n * sumTT - sumT * sumT;
        if (denom <= 0) {
            return null;
        }
        double criticalSpeed = (n * sumTD - sumT * sumD) / denom; // slope, m/s
        double dPrime = (sumD - criticalSpeed * sumT) / n;        // intercept, m
        if (criticalSpeed <= 0) {
            return null;
        }
        double meanD = sumD / n;
        double ssTot = 0, ssRes = 0;
        for (Map.Entry<Integer, Integer> entry : bestByDistance.entrySet()) {
            double d = entry.getKey();
            double predicted = criticalSpeed * entry.getValue() + dPrime;
            ssTot += (d - meanD) * (d - meanD);
            ssRes += (d - predicted) * (d - predicted);
        }
        double rSquared = ssTot > 0 ? 1 - ssRes / ssTot : 0;
        return new CriticalPace((int) Math.round(1000 / criticalSpeed),
                Math.round(criticalSpeed * 100) / 100.0, (int) Math.round(dPrime), n,
                Math.round(rSquared * 100) / 100.0);
    }

    /* ── Aerobic durability / late-run fade (P6) ───────────────────────── */

    /**
     * Aerobic decoupling on the long runs of the last {@value #DURABILITY_MONTHS}
     * months, oldest first. Each point is how much efficiency (speed ÷ HR)
     * dropped from the first half to the second; positive means the athlete
     * faded, under ~5 % is durable.
     */
    private static List<DurabilityPoint> durability(List<Activity> activities, LocalDate today,
                                                    int monthCount) {
        LocalDate from = today.minusMonths(monthCount);
        return activities.stream()
                .filter(a -> !a.getDate().isBefore(from) && !a.getDate().isAfter(today))
                .filter(a -> a.getDurationMin() >= DURABILITY_MIN_MIN && a.getStreamsJson() != null)
                .sorted(Comparator.comparing(Activity::getDate))
                .map(a -> {
                    Double decoupling = decoupling(a.getStreamsJson());
                    return decoupling == null ? null : new DurabilityPoint(a.getDate(), decoupling,
                            a.getDistanceKm() != null
                                    ? a.getDistanceKm().setScale(1, RoundingMode.HALF_UP) : null,
                            a.getDurationMin());
                })
                .filter(point -> point != null)
                .toList();
    }

    /** First-half vs second-half efficiency (speed ÷ HR) drop, in percent, or null. */
    static Double decoupling(String streamsJson) {
        try {
            JsonNode root = MAPPER.readTree(streamsJson);
            JsonNode time = root.path("time");
            JsonNode distance = root.path("distance");
            JsonNode hr = root.path("hr");
            int n = time.size();
            if (!time.isArray() || !distance.isArray() || !hr.isArray()
                    || n < 4 || distance.size() != n || hr.size() != n) {
                return null;
            }
            double midTime = time.get(n - 1).asDouble() / 2;
            int split = 1;
            while (split < n - 1 && time.get(split).asDouble() < midTime) {
                split++;
            }
            double t1 = time.get(split).asDouble() - time.get(0).asDouble();
            double t2 = time.get(n - 1).asDouble() - time.get(split).asDouble();
            double d1 = distance.get(split).asDouble() - distance.get(0).asDouble();
            double d2 = distance.get(n - 1).asDouble() - distance.get(split).asDouble();
            double hr1 = meanHr(hr, 0, split + 1);
            double hr2 = meanHr(hr, split, n);
            if (t1 <= 0 || t2 <= 0 || d1 <= 0 || d2 <= 0 || hr1 <= 0 || hr2 <= 0) {
                return null;
            }
            double eff1 = (d1 / t1) / hr1;
            double eff2 = (d2 / t2) / hr2;
            return eff1 <= 0 ? null : Math.round((eff1 - eff2) / eff1 * 1000) / 10.0;
        } catch (Exception e) {
            return null;
        }
    }

    private static double meanHr(JsonNode hr, int fromInclusive, int toExclusive) {
        double sum = 0;
        int count = 0;
        for (int i = fromInclusive; i < toExclusive && i < hr.size(); i++) {
            double value = hr.get(i).asDouble();
            if (value > 0) {
                sum += value;
                count++;
            }
        }
        return count > 0 ? sum / count : 0;
    }

    /* ── Volume & efficiency ───────────────────────────────────────────── */

    private static List<WeekVolume> weeklyVolume(List<Activity> activities, LocalDate today,
                                                 int weekCount) {
        LocalDate currentWeekStart = today.with(DayOfWeek.MONDAY);
        Map<LocalDate, List<Activity>> byWeek = new LinkedHashMap<>();
        for (int i = weekCount - 1; i >= 0; i--) {
            byWeek.put(currentWeekStart.minusWeeks(i), new ArrayList<>());
        }
        for (Activity activity : activities) {
            List<Activity> bucket = byWeek.get(activity.getDate().with(DayOfWeek.MONDAY));
            if (bucket != null) {
                bucket.add(activity);
            }
        }
        return byWeek.entrySet().stream().map(entry -> {
            List<Activity> runs = entry.getValue();
            BigDecimal km = runs.stream().map(Activity::getDistanceKm)
                    .filter(d -> d != null)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(1, RoundingMode.HALF_UP);
            int elevation = runs.stream().filter(a -> a.getElevationM() != null)
                    .mapToInt(Activity::getElevationM).sum();
            int duration = runs.stream().mapToInt(Activity::getDurationMin).sum();
            BigDecimal kmEffort = km.add(BigDecimal.valueOf(elevation / 100.0))
                    .setScale(1, RoundingMode.HALF_UP);
            return new WeekVolume(entry.getKey(), km, elevation, duration, kmEffort, runs.size());
        }).toList();
    }

    /** Metres per heartbeat on runs with HR — the aerobic-efficiency trend. */
    private static List<MonthEfficiency> efficiency(List<Activity> activities, LocalDate today,
                                                    int monthCount) {
        Map<YearMonth, List<Activity>> byMonth = new LinkedHashMap<>();
        YearMonth current = YearMonth.from(today);
        for (int i = monthCount - 1; i >= 0; i--) {
            byMonth.put(current.minusMonths(i), new ArrayList<>());
        }
        for (Activity activity : activities) {
            if (activity.getAvgHr() == null || activity.getDistanceKm() == null
                    || activity.getDistanceKm().signum() <= 0 || activity.getDurationMin() <= 0) {
                continue;
            }
            List<Activity> bucket = byMonth.get(YearMonth.from(activity.getDate()));
            if (bucket != null) {
                bucket.add(activity);
            }
        }
        return byMonth.entrySet().stream().map(entry -> {
            List<Activity> runs = entry.getValue();
            if (runs.isEmpty()) {
                return new MonthEfficiency(entry.getKey().toString(), null, 0);
            }
            double sum = runs.stream()
                    .mapToDouble(a -> (a.getDistanceKm().doubleValue() * 1000 / a.getDurationMin())
                            / a.getAvgHr())
                    .sum();
            return new MonthEfficiency(entry.getKey().toString(),
                    BigDecimal.valueOf(sum / runs.size()).setScale(2, RoundingMode.HALF_UP),
                    runs.size());
        }).toList();
    }

    /* ── Duration checkpoints ("after 1 h you are at…") ────────────────── */

    private static List<DurationCheckpoint> checkpoints(List<Activity> activities) {
        List<DurationCheckpoint> result = new ArrayList<>();
        for (int minutes : CHECKPOINTS_MIN) {
            List<double[]> samples = new ArrayList<>(); // [distanceKm, elevationM]
            for (Activity activity : activities) {
                double[] sample = checkpointSample(activity, minutes);
                if (sample != null) {
                    samples.add(sample);
                }
            }
            if (samples.isEmpty()) {
                continue;
            }
            double km = median(samples.stream().map(s -> s[0]).toList());
            double elevation = median(samples.stream().map(s -> s[1]).toList());
            Integer pace = km > 0 ? (int) Math.round(minutes * 60 / km) : null;
            result.add(new DurationCheckpoint(minutes, samples.size(),
                    BigDecimal.valueOf(km).setScale(1, RoundingMode.HALF_UP),
                    (int) Math.round(elevation), pace));
        }
        return result;
    }

    /**
     * Where this run stood at the checkpoint: exact when streams exist,
     * else the whole run when its duration is within ±10 % of the mark.
     */
    private static double[] checkpointSample(Activity activity, int minutes) {
        if (activity.getStreamsJson() != null && activity.getDurationMin() >= minutes) {
            double[] fromStreams = fromStreams(activity.getStreamsJson(), minutes * 60);
            if (fromStreams != null) {
                return fromStreams;
            }
        }
        if (activity.getDistanceKm() == null) {
            return null;
        }
        if (Math.abs(activity.getDurationMin() - minutes) <= minutes * 0.1) {
            return new double[]{activity.getDistanceKm().doubleValue(),
                    activity.getElevationM() != null ? activity.getElevationM() : 0};
        }
        return null;
    }

    /** Distance and cumulated D+ at t seconds, from the downsampled streams. */
    static double[] fromStreams(String streamsJson, int atSeconds) {
        try {
            JsonNode root = MAPPER.readTree(streamsJson);
            JsonNode time = root.path("time");
            JsonNode distance = root.path("distance");
            JsonNode alt = root.path("alt");
            if (!time.isArray() || !distance.isArray() || time.size() < 2
                    || time.get(time.size() - 1).asDouble() < atSeconds) {
                return null;
            }
            double km = 0;
            double dplus = 0;
            double previousAlt = alt.isArray() && alt.size() > 0 ? alt.get(0).asDouble() : Double.NaN;
            for (int i = 1; i < time.size(); i++) {
                if (time.get(i).asDouble() > atSeconds) {
                    // linear interpolation on the crossing segment
                    double t0 = time.get(i - 1).asDouble();
                    double share = (atSeconds - t0) / (time.get(i).asDouble() - t0);
                    double d0 = distance.get(i - 1).asDouble();
                    km = (d0 + share * (distance.get(i).asDouble() - d0)) / 1000.0;
                    break;
                }
                km = distance.get(i).asDouble() / 1000.0;
                if (alt.isArray() && i < alt.size()) {
                    double current = alt.get(i).asDouble();
                    if (!Double.isNaN(previousAlt) && current > previousAlt) {
                        dplus += current - previousAlt;
                    }
                    previousAlt = current;
                }
            }
            return new double[]{km, dplus};
        } catch (Exception e) {
            return null;
        }
    }

    /* ── Predictions ───────────────────────────────────────────────────── */

    private static double recentWeeklyKm(List<Activity> activities, LocalDate today) {
        LocalDate from = today.minusWeeks(8);
        double km = activities.stream()
                .filter(a -> !a.getDate().isBefore(from) && a.getDistanceKm() != null)
                .mapToDouble(a -> a.getDistanceKm().doubleValue())
                .sum();
        return km / 8;
    }

    /**
     * Vickers-Vertosick-style exponent: their study of 2 000+ recreational
     * runners found the Riegel exponent understates fatigue at low training
     * volume (1.07–1.09 fits the average recreational runner). Exact
     * regression coefficients aren't published in reusable form, so this is
     * a transparent approximation: 1.06 at 80 km/week, +0.01 per ~20 km less.
     */
    static double vickersExponent(double weeklyKm) {
        double k = 1.06 + Math.max(0, (80 - weeklyKm) / 20 * 0.01);
        return Math.min(k, 1.10);
    }

    /** Cameron's pace-ratio model, f(x) = 13.49681 − 0.000030363·x + 835.7114/x^0.7905. */
    static int cameron(int baseSec, int baseM, int targetM) {
        double fBase = 13.49681 - 0.000030363 * baseM + 835.7114 / Math.pow(baseM, 0.7905);
        double fTarget = 13.49681 - 0.000030363 * targetM + 835.7114 / Math.pow(targetM, 0.7905);
        return (int) Math.round(((double) baseSec / baseM) * targetM * (fBase / fTarget));
    }

    private static List<RoadPrediction> roadPredictions(List<DistanceRecord> records,
                                                        double weeklyKm) {
        if (records.isEmpty()) {
            return List.of();
        }
        double vickersK = vickersExponent(weeklyKm);
        List<RoadPrediction> predictions = new ArrayList<>();
        for (RoadTarget target : ROAD_TARGETS) {
            DistanceRecord base = records.stream()
                    .filter(r -> r.distanceM() != target.distanceM())
                    .min(Comparator.comparingDouble(r ->
                            Math.abs(Math.log((double) target.distanceM() / r.distanceM()))))
                    .orElse(null);
            if (base == null) {
                continue;
            }
            Integer record = records.stream()
                    .filter(r -> r.distanceM() == target.distanceM())
                    .map(DistanceRecord::seconds)
                    .findFirst().orElse(null);
            double ratio = (double) target.distanceM() / base.distanceM();
            predictions.add(new RoadPrediction(target.label(), target.distanceM(),
                    base.label(), base.seconds(),
                    (int) Math.round(base.seconds() * Math.pow(ratio, RIEGEL_K)),
                    cameron(base.seconds(), base.distanceM(), target.distanceM()),
                    (int) Math.round(base.seconds() * Math.pow(ratio, vickersK)),
                    record));
        }
        return predictions;
    }

    /**
     * The athlete's trail pacing: seconds per km-effort (km + D+/100) at the
     * 25th/50th/75th percentile of recent hilly long runs, the median km-effort
     * they came from, and the ultra-fatigue exponent. The objective estimates
     * and the GPX course pacing (P12) both scale from this.
     */
    public record TrailPace(double q1SecPerKmEffort, double medianSecPerKmEffort,
                            double q3SecPerKmEffort, double baseKmEffort, double fatigueK,
                            int sampleRuns) {

        /** Riegel-style scale-up for an effort bigger than the athlete's usual. */
        public double fatigueFor(double kmEffort) {
            return Math.pow(kmEffort / baseKmEffort, fatigueK - 1);
        }
    }

    /** The trail pacing model for one athlete — null under 3 qualifying runs. */
    @Transactional(readOnly = true)
    public TrailPace trailPace(UUID userId, LocalDate today) {
        List<Activity> runs = activityRepository.findByUserId(userId).stream()
                .filter(Activity::isRun)
                .toList();
        return trailPace(runs, today);
    }

    static TrailPace trailPace(List<Activity> runs, LocalDate today) {
        List<Activity> trailRuns = runs.stream()
                .filter(a -> !a.getDate().isBefore(today.minusMonths(6)))
                .filter(a -> a.getDistanceKm() != null && a.getDistanceKm().doubleValue() >= 8)
                .filter(a -> a.getElevationM() != null
                        && a.getElevationM() / a.getDistanceKm().doubleValue() >= TRAIL_DPLUS_PER_KM)
                .filter(a -> a.getDurationMin() >= 60)
                .toList();
        if (trailRuns.size() < 3) {
            return null;
        }
        List<Double> paces = trailRuns.stream()
                .map(a -> a.getDurationMin() * 60.0
                        / (a.getDistanceKm().doubleValue() + a.getElevationM() / 100.0))
                .sorted()
                .toList();
        double baseKmEffort = median(trailRuns.stream()
                .map(a -> a.getDistanceKm().doubleValue() + a.getElevationM() / 100.0)
                .toList());
        return new TrailPace(paces.get(paces.size() / 4), median(paces), paces.get(paces.size() * 3 / 4),
                baseKmEffort, TRAIL_K, trailRuns.size());
    }

    /**
     * Trail objectives, timed from the athlete's own trail pacing scaled to the
     * objective's km-effort with a Riegel-style fatigue exponent.
     */
    private List<TrailEstimate> trailEstimates(List<Activity> activities,
                                               List<Objective> objectives, LocalDate today) {
        TrailPace pace = trailPace(activities, today);
        if (pace == null) {
            return List.of();
        }
        return objectives.stream()
                .filter(o -> o.getDate() != null && !o.getDate().isBefore(today))
                .filter(o -> o.getDistanceKm() != null)
                .sorted(Comparator.comparing(Objective::getDate))
                .map(o -> {
                    double kmEffort = o.getDistanceKm().doubleValue()
                            + (o.getElevationGainM() != null ? o.getElevationGainM() / 100.0 : 0);
                    double fatigue = pace.fatigueFor(kmEffort);
                    return new TrailEstimate(o.getName(), o.getDate(), o.getDistanceKm(),
                            o.getElevationGainM(),
                            BigDecimal.valueOf(kmEffort).setScale(0, RoundingMode.HALF_UP),
                            (int) Math.round(pace.q1SecPerKmEffort() * kmEffort * fatigue),
                            (int) Math.round(pace.medianSecPerKmEffort() * kmEffort * fatigue),
                            (int) Math.round(pace.q3SecPerKmEffort() * kmEffort * fatigue),
                            pace.sampleRuns());
                })
                .toList();
    }

    /* ── Small math ────────────────────────────────────────────────────── */

    private static double median(List<Double> sorted) {
        List<Double> values = sorted.stream().sorted().toList();
        int n = values.size();
        return n % 2 == 1 ? values.get(n / 2) : (values.get(n / 2 - 1) + values.get(n / 2)) / 2;
    }

    private static double round1(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
