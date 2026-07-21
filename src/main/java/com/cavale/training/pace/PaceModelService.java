package com.cavale.training.pace;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cavale.common.AppTime;
import com.cavale.training.domain.Activity;
import com.cavale.training.domain.Discipline;
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

    private final ActivityRepository activityRepository;

    public PaceModelService(ActivityRepository activityRepository) {
        this.activityRepository = activityRepository;
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
        return fit(easy);
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
