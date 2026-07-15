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
import com.cavale.athlete.dto.RunningStatsResponse.WeekEffort;
import com.cavale.athlete.dto.RunningStatsResponse.WeekMonotony;
import com.cavale.athlete.dto.RunningStatsResponse.WeekVolume;
import com.cavale.training.domain.Activity;
import com.cavale.training.domain.Objective;
import com.cavale.training.repository.ActivityBestEffortRepository;
import com.cavale.training.repository.ActivityRepository;
import com.cavale.training.repository.ObjectiveRepository;

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

    public RunningStatsService(ActivityRepository activityRepository,
                               ActivityBestEffortRepository bestEffortRepository,
                               ObjectiveRepository objectiveRepository) {
        this.activityRepository = activityRepository;
        this.bestEffortRepository = bestEffortRepository;
        this.objectiveRepository = objectiveRepository;
    }

    @Transactional(readOnly = true)
    public RunningStatsResponse getStats(UUID userId) {
        return getStats(userId, LocalDate.now());
    }

    @Transactional(readOnly = true)
    public RunningStatsResponse getStats(UUID userId, LocalDate today) {
        List<Activity> activities = activityRepository.findByUserId(userId).stream()
                .sorted(Comparator.comparing(Activity::getDate))
                .toList();
        List<DistanceRecord> roadRecords = AthleteStatsService
                .roadRecords(bestEffortRepository.findByUserId(userId));
        List<Objective> objectives = objectiveRepository.findByUserId(userId);

        double weeklyKm = recentWeeklyKm(activities, today);
        List<DayForm> form = form(activities, today);
        Acwr acwr = acwr(activities, today);
        return new RunningStatsResponse(
                form,
                weeklyEffort(activities, today),
                acwr,
                weeklyVolume(activities, today),
                efficiency(activities, today),
                checkpoints(activities),
                roadPredictions(roadRecords, weeklyKm),
                trailEstimates(activities, objectives, today),
                monotony(activities, today),
                trainingStatus(form, acwr));
    }

    /* ── Training load (Banister / Strava F&F) ─────────────────────────── */

    /** Daily relative effort; HR-less runs get a duration-based estimate. */
    static int dailyEffort(Activity activity) {
        return activity.getRelativeEffort() != null
                ? activity.getRelativeEffort()
                : (int) Math.round(activity.getDurationMin() * ESTIMATED_RE_PER_MIN);
    }

    private static List<DayForm> form(List<Activity> activities, LocalDate today) {
        // warm up the averages well before the visible window
        LocalDate warmupStart = today.minusDays(FORM_DAYS + 3L * (long) FITNESS_TAU);
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
        List<DayForm> series = new ArrayList<>(FORM_DAYS);
        LocalDate windowStart = today.minusDays(FORM_DAYS - 1L);
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
    private static List<WeekEffort> weeklyEffort(List<Activity> activities, LocalDate today) {
        LocalDate currentWeekStart = today.with(DayOfWeek.MONDAY);
        Map<LocalDate, int[]> weeks = new LinkedHashMap<>(); // [effort, estimatedCount]
        for (int i = WEEKS + 2; i >= 0; i--) { // +3 weeks of history for the first band
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
        List<WeekEffort> series = new ArrayList<>(WEEKS);
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
    private static List<WeekMonotony> monotony(List<Activity> activities, LocalDate today) {
        Map<LocalDate, Integer> effortByDay = new HashMap<>();
        for (Activity activity : activities) {
            effortByDay.merge(activity.getDate(), dailyEffort(activity), Integer::sum);
        }
        LocalDate currentWeekStart = today.with(DayOfWeek.MONDAY);
        List<WeekMonotony> series = new ArrayList<>(WEEKS);
        for (int i = WEEKS - 1; i >= 0; i--) {
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

    /* ── Volume & efficiency ───────────────────────────────────────────── */

    private static List<WeekVolume> weeklyVolume(List<Activity> activities, LocalDate today) {
        LocalDate currentWeekStart = today.with(DayOfWeek.MONDAY);
        Map<LocalDate, List<Activity>> byWeek = new LinkedHashMap<>();
        for (int i = WEEKS - 1; i >= 0; i--) {
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
    private static List<MonthEfficiency> efficiency(List<Activity> activities, LocalDate today) {
        Map<YearMonth, List<Activity>> byMonth = new LinkedHashMap<>();
        YearMonth current = YearMonth.from(today);
        for (int i = EFFICIENCY_MONTHS - 1; i >= 0; i--) {
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
     * Trail objectives, timed from the athlete's own trail pacing: median
     * seconds per km-effort over recent hilly long runs, scaled to the
     * objective's km-effort with a Riegel-style fatigue exponent.
     */
    private List<TrailEstimate> trailEstimates(List<Activity> activities,
                                               List<Objective> objectives, LocalDate today) {
        List<Activity> trailRuns = activities.stream()
                .filter(a -> !a.getDate().isBefore(today.minusMonths(6)))
                .filter(a -> a.getDistanceKm() != null && a.getDistanceKm().doubleValue() >= 8)
                .filter(a -> a.getElevationM() != null
                        && a.getElevationM() / a.getDistanceKm().doubleValue() >= TRAIL_DPLUS_PER_KM)
                .filter(a -> a.getDurationMin() >= 60)
                .toList();
        if (trailRuns.size() < 3) {
            return List.of();
        }

        List<Double> paces = trailRuns.stream()
                .map(a -> a.getDurationMin() * 60.0
                        / (a.getDistanceKm().doubleValue() + a.getElevationM() / 100.0))
                .sorted()
                .toList();
        double medianPace = median(paces);
        double q1 = paces.get(paces.size() / 4);
        double q3 = paces.get(paces.size() * 3 / 4);
        double baseKmEffort = median(trailRuns.stream()
                .map(a -> a.getDistanceKm().doubleValue() + a.getElevationM() / 100.0)
                .toList());

        return objectives.stream()
                .filter(o -> o.getDate() != null && !o.getDate().isBefore(today))
                .filter(o -> o.getDistanceKm() != null)
                .sorted(Comparator.comparing(Objective::getDate))
                .map(o -> {
                    double kmEffort = o.getDistanceKm().doubleValue()
                            + (o.getElevationGainM() != null ? o.getElevationGainM() / 100.0 : 0);
                    double fatigue = Math.pow(kmEffort / baseKmEffort, TRAIL_K - 1);
                    return new TrailEstimate(o.getName(), o.getDate(), o.getDistanceKm(),
                            o.getElevationGainM(),
                            BigDecimal.valueOf(kmEffort).setScale(0, RoundingMode.HALF_UP),
                            (int) Math.round(q1 * kmEffort * fatigue),
                            (int) Math.round(medianPace * kmEffort * fatigue),
                            (int) Math.round(q3 * kmEffort * fatigue),
                            trailRuns.size());
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
