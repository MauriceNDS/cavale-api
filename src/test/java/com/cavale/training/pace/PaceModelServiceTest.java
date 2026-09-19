package com.cavale.training.pace;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.cavale.training.domain.Activity;
import com.cavale.training.domain.ActivityBestEffort;
import com.cavale.training.domain.Discipline;
import com.cavale.training.domain.PlannedSession;
import com.cavale.training.repository.ActivityRepository;
import com.cavale.training.workout.WorkoutStructure.Allure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaceModelServiceTest {

    private static final UUID USER = UUID.randomUUID();

    @Mock
    private ActivityRepository activityRepository;

    @Mock
    private com.cavale.training.repository.ActivityBestEffortRepository bestEffortRepository;

    private PaceModel model(List<Activity> runs) {
        when(activityRepository.findByUserIdAndDisciplineAndDateGreaterThanEqual(
                eq(USER), eq(Discipline.RUN), any(LocalDate.class))).thenReturn(runs);
        org.mockito.Mockito.lenient().when(bestEffortRepository.findByUserId(USER))
                .thenReturn(List.of()); // no efforts: quality paces stay ratio-derived
        return new PaceModelService(activityRepository, bestEffortRepository).modelFor(USER);
    }

    /** A 10 km run whose pace follows flat + slope×climb exactly. */
    private static Activity run(int daysAgo, double flatSecPerKm, double slopeSecPerMeter,
                                double climbPerKm, int avgHr) {
        double km = 10;
        double paceSecPerKm = flatSecPerKm + slopeSecPerMeter * climbPerKm;
        int durationMin = (int) Math.round(paceSecPerKm * km / 60);
        return Activity.manual(USER, LocalDate.now().minusDays(daysAgo), durationMin,
                BigDecimal.valueOf(km), (int) Math.round(climbPerKm * km), avgHr, "run");
    }

    @Test
    void recoversFlatPaceAndClimbCostFromEasyRuns() {
        List<Activity> runs = new ArrayList<>();
        // 25 easy runs (HR 138-148) on terrain from flat to 48 m/km, true model 360 + 2.0×climb
        for (int i = 0; i < 25; i++) {
            runs.add(run(i * 4 + 1, 360, 2.0, i * 2, 138 + i % 11));
        }
        // 5 hard runs way above the easy band — must be excluded by the quantile filter
        for (int i = 0; i < 5; i++) {
            runs.add(run(i * 7 + 2, 250, 2.0, 0, 172));
        }

        PaceModel model = model(runs);

        assertThat(model.personal()).isTrue();
        assertThat(model.secPerKm(Allure.EF)).isCloseTo(360, within(8));
        assertThat(model.climbSecPerMeter()).isCloseTo(2.0, within(0.4));
        // hard runs excluded: 25 easy in the fit, not 30
        assertThat(model.sampleSize()).isLessThanOrEqualTo(25);
    }

    @Test
    void fallsBackOnSparseHistory() {
        List<Activity> runs = List.of(
                run(1, 360, 2.0, 0, 140),
                run(3, 360, 2.0, 10, 140),
                run(5, 360, 2.0, 20, 140));

        PaceModel model = model(runs);

        assertThat(model.personal()).isFalse();
        assertThat(model.secPerKm(Allure.EF)).isEqualTo(PaceModel.DEFAULT_EF_SEC_PER_KM);
    }

    @Test
    void keepsDefaultClimbCostWhenAllRunsShareTheSameTerrain() {
        List<Activity> runs = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            runs.add(run(i * 5 + 1, 360, 2.0, 5, 140)); // all at 5 m/km — no slope signal
        }

        PaceModel model = model(runs);

        assertThat(model.personal()).isTrue();
        assertThat(model.climbSecPerMeter()).isEqualTo(PaceModel.DEFAULT_CLIMB_SEC_PER_METER);
        // intercept back-computed from the mean pace at 5 m/km with the default slope
        assertThat(model.secPerKm(Allure.EF)).isCloseTo(350, within(8));
    }

    @Test
    void quickerAlluresRunFasterThanEf() {
        List<Activity> runs = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            runs.add(run(i * 5 + 1, 360, 2.0, i * 2, 140));
        }

        PaceModel model = model(runs);

        assertThat(model.secPerKm(Allure.LENTE)).isGreaterThan(model.secPerKm(Allure.EF));
        assertThat(model.secPerKm(Allure.SEUIL30)).isLessThan(model.secPerKm(Allure.EF));
        assertThat(model.secPerKm(Allure.VMA)).isLessThan(model.secPerKm(Allure.SEUIL30));
    }

    /* ── Critical speed ─────────────────────────────────────────────────── */

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 19);

    private PaceModelService service() {
        return new PaceModelService(activityRepository, bestEffortRepository);
    }

    /** A flat 10 km run with exact moving seconds — the parent of best efforts. */
    private static Activity flatRun(LocalDate date, int durationMin, String name) {
        Activity activity = Activity.stravaHistory(USER, date, durationMin, BigDecimal.TEN, 30, 150,
                name, date.toEpochDay());
        activity.recordMovingSeconds(durationMin * 60);
        return activity;
    }

    @Test
    void criticalSpeed_fitsTheTwoToFortyMinuteCurve() {
        Activity run = flatRun(TODAY.minusDays(5), 60, "Sortie");
        List<ActivityBestEffort> efforts = List.of(
                new ActivityBestEffort(run, "1k", 1000, 200),
                new ActivityBestEffort(run, "5k", 5000, 1100),
                new ActivityBestEffort(run, "10k", 10000, 2400),
                // a 42 km "best effort" is training pace: must stay out of the fit
                new ActivityBestEffort(run, "marathon", 42195, 15000));

        PaceModelService.CriticalSpeed cs = service().criticalSpeed(efforts, TODAY);

        assertThat(cs).isNotNull();
        assertThat(cs.anchored()).isFalse();
        assertThat(cs.samples()).isEqualTo(3);
        assertThat(cs.speedMps()).isBetween(3.5, 5.0);
        assertThat(cs.secPerKm()).isBetween(200, 290);
        assertThat(cs.rSquared()).isGreaterThan(0.9);
    }

    @Test
    void criticalSpeed_nullWithoutEnoughDistances() {
        Activity run = flatRun(TODAY.minusDays(5), 60, "Sortie");
        List<ActivityBestEffort> efforts = List.of(
                new ActivityBestEffort(run, "1k", 1000, 200),
                new ActivityBestEffort(run, "5k", 5000, 1100));

        assertThat(service().criticalSpeed(efforts, TODAY)).isNull();
    }

    @Test
    void criticalSpeed_recentTestNamedBySessionAnchorsOnItsBestThirtyMinutes() {
        // a 30′ time trial validated against a planned "★ RE-TEST LTHR 30′" —
        // the synced activity itself keeps the watch's generic name
        Activity test = flatRun(TODAY.minusDays(1), 67, "Morning Run");
        PlannedSession session = new PlannedSession(null, USER, TODAY.minusDays(1), 0,
                Discipline.RUN, "★ RE-TEST LTHR 30′", null, "SEUIL30", 60, null, null, null);
        test.attachToSession(session);
        // 20′ warm-up at 6:00/km, 30′ block at 4:13/km (7 110 m), 10′ cool-down at 6:00/km
        StringBuilder time = new StringBuilder("[");
        StringBuilder dist = new StringBuilder("[");
        double d = 0;
        for (int t = 0; t <= 3600; t += 10) {
            double v = t < 1200 ? 1000.0 / 360 : t < 3000 ? 1000.0 / 253 : 1000.0 / 360;
            if (t > 0) {
                d += v * 10;
                time.append(',');
                dist.append(',');
            }
            time.append(t);
            dist.append(String.format(java.util.Locale.ROOT, "%.1f", d));
        }
        test.attachStreams("{\"time\":" + time + "],\"distance\":" + dist + "]}");
        Activity older = flatRun(TODAY.minusDays(40), 60, "Sortie");
        List<ActivityBestEffort> efforts = List.of(
                // the 5 km split inside the block: Riegel alone would project it to ~4:17
                new ActivityBestEffort(test, "5k", 5000, 1265),
                new ActivityBestEffort(test, "1k", 1000, 250),
                new ActivityBestEffort(older, "1k", 1000, 215),
                new ActivityBestEffort(older, "5k", 5000, 1320),
                new ActivityBestEffort(older, "10k", 10000, 2390));

        PaceModelService.CriticalSpeed cs = service().criticalSpeed(efforts, TODAY);

        assertThat(cs).isNotNull();
        assertThat(cs.anchored()).isTrue();
        assertThat(cs.secPerKm()).isEqualTo(253);
        assertThat(cs.samples()).isEqualTo(3); // the fit still reports its own basis
    }

    @Test
    void criticalSpeed_testOlderThanEightWeeksNoLongerAnchors() {
        Activity test = flatRun(TODAY.minusDays(60), 67, "Test LTHR 30′");
        Activity older = flatRun(TODAY.minusDays(40), 60, "Sortie");
        List<ActivityBestEffort> efforts = List.of(
                new ActivityBestEffort(test, "5k", 5000, 1265),
                new ActivityBestEffort(older, "1k", 1000, 215),
                new ActivityBestEffort(older, "5k", 5000, 1320),
                new ActivityBestEffort(older, "10k", 10000, 2390));

        assertThat(service().criticalSpeed(efforts, TODAY).anchored()).isFalse();
    }

    @Test
    void bestWindow_interpolatesTheWindowEnds() {
        // 5 000 m in 1 200 s then a crawl: the best 600 s window is 2 500 m → 240 s/km
        Double pace = PaceModelService.bestWindowSecPerKm(
                "{\"time\":[0,1200,2400],\"distance\":[0,5000,5500]}", 600);

        assertThat(pace).isCloseTo(240.0, within(0.5));
        assertThat(PaceModelService.bestWindowSecPerKm(
                "{\"time\":[0,300],\"distance\":[0,1000]}", 600)).isNull();
        assertThat(PaceModelService.bestWindowSecPerKm(null, 600)).isNull();
    }
}
