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
import com.cavale.training.domain.Discipline;
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
}
