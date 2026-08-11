package com.cavale.training.pace;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cavale.common.AppTime;
import com.cavale.training.domain.Activity;
import com.cavale.training.domain.ActivityBestEffort;
import com.cavale.training.domain.Discipline;
import com.cavale.training.repository.ActivityBestEffortRepository;
import com.cavale.training.repository.ActivityRepository;

/**
 * Learns the athlete's {@link PaceModel} from recent runs. Easy runs are
 * selected by a quantile HR band (5th–70th percentile of the window's average
 * HRs) rather than fixed zones — the athlete's max HR is often unset and
 * absolute thresholds go stale as fitness drifts. An OLS fit of pace against
 * climb-per-km over those runs yields the flat EF pace (intercept) and the
 * personal climb cost (slope, seconds per vertical metre).
 *
 * The window is deliberately short (120 days, widened to 240 only when
 * sparse): this athlete's easy pace moved ~45 s/km in 18 months, so recency
 * beats sample size. Anything under {@value #MIN_EASY_RUNS} usable runs falls
 * back to conservative defaults, flagged {@code personal=false}.
 */
@Service
public class PaceModelService {

    private static final int WINDOW_DAYS = 120;
    private static final int WIDE_WINDOW_DAYS = 240;
    private static final int MIN_EASY_RUNS = 15;

    private static final double MIN_DISTANCE_KM = 3;
    private static final int MIN_DURATION_MIN = 20;

    /** Fit guards: outside these, the data is degenerate — keep the sane part. */
    private static final int MIN_FLAT_SEC_PER_KM = 200;
    private static final int MAX_FLAT_SEC_PER_KM = 600;
    private static final double MIN_CLIMB_SEC_PER_METER = 0.3;
    private static final double MAX_CLIMB_SEC_PER_METER = 8.0;
    /** Below this spread of climb-per-km the slope is noise, not signal. */
    private static final double MIN_CLIMB_SPREAD = 3.0;

    /** CP fit guards: recency window, then plausibility of the fitted speed. */
    private static final int CP_WINDOW_DAYS = 180;
    private static final int CP_WIDE_WINDOW_DAYS = 365;
    private static final int CP_MIN_DISTANCES = 3;
    private static final double CP_MIN_SPEED_MPS = 2.2;
    private static final double CP_MAX_SPEED_MPS = 7.0;
    /**
     * The 2-parameter model is only valid on 2-40 minute efforts. Longer
     * "best efforts" are an ultra runner's training paces, not max efforts,
     * and their leverage drags the fitted speed down to easy pace.
     */
    private static final int CP_MIN_EFFORT_SEC = 120;
    private static final int CP_MAX_EFFORT_SEC = 2400;

    /** A recent designated max effort (race or field test) beats the regression. */
    private static final int TEST_ANCHOR_DAYS = 56;
    private static final java.util.regex.Pattern TEST_NAME = java.util.regex.Pattern
            .compile("(?i)test|lthr|\\btt\\b|race|comp[ée]t");
    private static final int TEST_MIN_EFFORT_SEC = 900;
    private static final int TEST_MAX_EFFORT_SEC = 2700;
    /** Riegel pace-drift exponent: pace30 = pace × (1800/t)^RIEGEL. */
    private static final double RIEGEL = 0.0566;

    private final ActivityRepository activityRepository;
    private final ActivityBestEffortRepository bestEffortRepository;

    public PaceModelService(ActivityRepository activityRepository,
                            ActivityBestEffortRepository bestEffortRepository) {
        this.activityRepository = activityRepository;
        this.bestEffortRepository = bestEffortRepository;
    }

    @Transactional(readOnly = true)
    public PaceModel modelFor(UUID userId) {
        LocalDate today = LocalDate.now(AppTime.ZONE);
        List<Activity> runs = activityRepository
                .findByUserIdAndDisciplineAndDateGreaterThanEqual(
                        userId, Discipline.RUN, today.minusDays(WIDE_WINDOW_DAYS))
                .stream()
                .filter(PaceModelService::usable)
                .toList();

        List<Activity> easy = easyRuns(runs, today.minusDays(WINDOW_DAYS));
        if (easy.size() < MIN_EASY_RUNS) {
            easy = easyRuns(runs, today.minusDays(WIDE_WINDOW_DAYS));
        }
        if (easy.size() < MIN_EASY_RUNS) {
            return PaceModel.fallback();
        }
        PaceModel efModel = fit(easy);
        return withCriticalSpeed(efModel, userId, today);
    }

    /**
     * Anchors the quality end on the critical speed fitted over recent
     * road-like best efforts. Distrusted (ratio fallback) when the fit is
     * degenerate or not meaningfully faster than the easy pace.
     */
    private PaceModel withCriticalSpeed(PaceModel efModel, UUID userId, LocalDate today) {
        List<ActivityBestEffort> efforts = bestEffortRepository.findByUserId(userId);
        CpFit fit = cpFit(efforts, today.minusDays(CP_WINDOW_DAYS));
        if (fit == null) {
            fit = cpFit(efforts, today.minusDays(CP_WIDE_WINDOW_DAYS));
        }
        // A fresh designated max effort (race / field test) is ground truth —
        // it overrides the regression while it lasts, then ages out of scope
        // and the fit (which contains its points anyway) takes back over.
        Integer anchor = testAnchorSecPerKm(efforts, today.minusDays(TEST_ANCHOR_DAYS));
        Integer cpSecPerKm = anchor != null ? anchor
                : fit != null ? (int) Math.round(1000.0 / fit.speedMps()) : null;
        if (cpSecPerKm == null) {
            return efModel;
        }
        int efSecPerKm = efModel.secPerKm(com.cavale.training.workout.WorkoutStructure.Allure.EF);
        if (cpSecPerKm >= efSecPerKm * 0.97) {
            return efModel; // a threshold barely faster than easy is noise, not fitness
        }
        return PaceModel.anchored(efSecPerKm, cpSecPerKm, fit != null ? fit.dPrimeM() : null,
                efModel.climbSecPerMeter(), efModel.sampleSize());
    }

    /**
     * Best 30-minute pace projected (Riegel) from the strongest 15-45′ effort
     * inside a race or test activity — null when none is recent enough.
     */
    private static Integer testAnchorSecPerKm(List<ActivityBestEffort> efforts, LocalDate from) {
        double best = Double.MAX_VALUE;
        for (ActivityBestEffort effort : efforts) {
            Activity activity = effort.getActivity();
            boolean designated = activity.isRace()
                    || (activity.getName() != null && TEST_NAME.matcher(activity.getName()).find());
            if (activity.getDate().isBefore(from)
                    || effort.getElapsedSec() < TEST_MIN_EFFORT_SEC
                    || effort.getElapsedSec() > TEST_MAX_EFFORT_SEC
                    || effort.getDistanceM() <= 0
                    || !designated
                    || !com.cavale.athlete.service.AthleteStatsService.isRoadLike(effort)) {
                continue;
            }
            double paceSecPerKm = effort.getElapsedSec() * 1000.0 / effort.getDistanceM();
            double pace30 = paceSecPerKm * Math.pow(1800.0 / effort.getElapsedSec(), RIEGEL);
            best = Math.min(best, pace30);
        }
        return best < Double.MAX_VALUE ? (int) Math.round(best) : null;
    }

    private record CpFit(double speedMps, Double dPrimeM) {
    }

    /**
     * 2-parameter critical-speed fit (distance = CS × time + D′) over the
     * fastest road-like effort at each distance, same least-squares as the
     * stats page. Null when the data can't support it.
     */
    private CpFit cpFit(List<ActivityBestEffort> efforts, LocalDate from) {
        Map<Integer, Integer> bestByDistance = new java.util.HashMap<>();
        for (ActivityBestEffort effort : efforts) {
            if (effort.getActivity().getDate().isBefore(from)
                    || effort.getElapsedSec() < CP_MIN_EFFORT_SEC
                    || effort.getElapsedSec() > CP_MAX_EFFORT_SEC
                    || !com.cavale.athlete.service.AthleteStatsService.isRoadLike(effort)) {
                continue;
            }
            bestByDistance.merge(effort.getDistanceM(), effort.getElapsedSec(), Math::min);
        }
        if (bestByDistance.size() < CP_MIN_DISTANCES) {
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
        double speed = (n * sumTD - sumT * sumD) / denom;
        double dPrime = (sumD - speed * sumT) / n;
        if (speed < CP_MIN_SPEED_MPS || speed > CP_MAX_SPEED_MPS) {
            return null;
        }
        return new CpFit(speed, dPrime > 0 ? dPrime : null);
    }

    private static boolean usable(Activity activity) {
        return activity.getDistanceKm() != null
                && activity.getDistanceKm().doubleValue() >= MIN_DISTANCE_KM
                && activity.getDurationMin() >= MIN_DURATION_MIN
                && activity.getAvgHr() != null;
    }

    /** Runs whose average HR sits in the window's 5th–70th percentile band. */
    private static List<Activity> easyRuns(List<Activity> runs, LocalDate from) {
        List<Activity> window = runs.stream()
                .filter(a -> !a.getDate().isBefore(from))
                .toList();
        if (window.size() < MIN_EASY_RUNS) {
            return List.of();
        }
        List<Integer> hrs = window.stream().map(Activity::getAvgHr).sorted().toList();
        int low = hrs.get((int) (hrs.size() * 0.05));
        int high = hrs.get((int) (hrs.size() * 0.70));
        return window.stream()
                .filter(a -> a.getAvgHr() >= low && a.getAvgHr() <= high)
                .toList();
    }

    /** OLS of pace (s/km) against climb (m/km): intercept = flat EF, slope = s per vertical m. */
    private static PaceModel fit(List<Activity> easy) {
        int n = easy.size();
        double[] climbPerKm = new double[n];
        double[] pace = new double[n];
        double climbMin = Double.MAX_VALUE;
        double climbMax = -Double.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            Activity a = easy.get(i);
            double km = a.getDistanceKm().doubleValue();
            climbPerKm[i] = (a.getElevationM() != null ? a.getElevationM() : 0) / km;
            pace[i] = a.getDurationMin() * 60 / km;
            climbMin = Math.min(climbMin, climbPerKm[i]);
            climbMax = Math.max(climbMax, climbPerKm[i]);
        }

        double meanClimb = mean(climbPerKm);
        double meanPace = mean(pace);
        double sxx = 0;
        double sxy = 0;
        for (int i = 0; i < n; i++) {
            sxx += (climbPerKm[i] - meanClimb) * (climbPerKm[i] - meanClimb);
            sxy += (climbPerKm[i] - meanClimb) * (pace[i] - meanPace);
        }

        double slope;
        double intercept;
        if (climbMax - climbMin < MIN_CLIMB_SPREAD || sxx == 0) {
            // All runs on similar terrain: the mean pace is trustworthy at that
            // climb rate, but the slope isn't — keep the default climb cost.
            slope = PaceModel.DEFAULT_CLIMB_SEC_PER_METER;
            intercept = meanPace - slope * meanClimb;
        } else {
            slope = Math.clamp(sxy / sxx, MIN_CLIMB_SEC_PER_METER, MAX_CLIMB_SEC_PER_METER);
            intercept = meanPace - slope * meanClimb;
        }
        int flat = (int) Math.round(Math.clamp(intercept, MIN_FLAT_SEC_PER_KM, MAX_FLAT_SEC_PER_KM));
        return PaceModel.of(flat, slope, n, true);
    }

    private static double mean(double[] values) {
        double sum = 0;
        for (double v : values) {
            sum += v;
        }
        return sum / values.length;
    }
}
