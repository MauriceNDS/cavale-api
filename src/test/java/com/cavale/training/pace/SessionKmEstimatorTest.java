package com.cavale.training.pace;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.cavale.training.domain.Discipline;
import com.cavale.training.domain.PlannedSession;
import com.cavale.training.workout.WorkoutJson;
import com.cavale.training.workout.WorkoutStructure.Allure;
import com.cavale.training.workout.WorkoutStructure.Node;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SessionKmEstimatorTest {

    /** EF at 6:00/km flat, 2 s per climbed metre. */
    private static final PaceModel MODEL = PaceModel.of(360, 2.0, 30, true);

    private static PlannedSession session(Discipline discipline, String zone, Integer durationMin,
                                          Integer elevationM, List<Node> workout) {
        PlannedSession session = new PlannedSession(null, UUID.randomUUID(), LocalDate.now(), 0,
                discipline, "Séance", null, zone, durationMin, elevationM, null, null);
        if (workout != null) {
            session.updateWorkoutJson(WorkoutJson.write(workout));
        }
        return session;
    }

    @Test
    void flatEasyHourAtSixMinPerKmIsTenKm() {
        PlannedSession run = session(Discipline.RUN, "EF", 60, 0,
                List.of(Node.step(Allure.EF, 3600, null)));

        assertThat(SessionKmEstimator.estimateKm(run, MODEL))
                .isEqualByComparingTo(new BigDecimal("10.0"));
    }

    @Test
    void climbingEatsIntoTheDistance() {
        // 500 m of D+ costs 1000 s of the 3600 s budget → 10 km × (1 − 0.278)
        PlannedSession run = session(Discipline.RUN, "EF", 60, 500,
                List.of(Node.step(Allure.EF, 3600, null)));

        assertThat(SessionKmEstimator.estimateKm(run, MODEL))
                .isEqualByComparingTo(new BigDecimal("7.2"));
    }

    @Test
    void intervalBlocksUseTheirOwnAllurePaces() {
        // 20 min EF + 8 × (3 min VMA / 1 min récup)
        PlannedSession run = session(Discipline.RUN, "VMA", 52, 0, List.of(
                Node.step(Allure.EF, 1200, null),
                Node.repeat(8, List.of(
                        Node.step(Allure.VMA, 180, null),
                        Node.step(Allure.LENTE, 60, null)))));

        BigDecimal km = SessionKmEstimator.estimateKm(run, MODEL);

        // 1200/360 + 8×(180/273 + 60/424) ≈ 9.7 — faster than 8.7 km of pure EF
        assertThat(km.doubleValue()).isCloseTo(9.7, within(0.2));
    }

    @Test
    void sessionWithoutStructureFallsBackToZoneAndDuration() {
        PlannedSession run = session(Discipline.RUN, "EF", 60, 0, null);

        assertThat(SessionKmEstimator.estimateKm(run, MODEL))
                .isEqualByComparingTo(new BigDecimal("10.0"));
    }

    @Test
    void structureMuchShorterThanTheSessionIsPaddedWithEasyRunning() {
        // 20 min of parsed blocks inside a 60 min session → 40 easy minutes added
        PlannedSession run = session(Discipline.RUN, "EF", 60, 0,
                List.of(Node.step(Allure.EF, 1200, null)));

        assertThat(SessionKmEstimator.estimateKm(run, MODEL))
                .isEqualByComparingTo(new BigDecimal("10.0"));
    }

    @Test
    void nonRunSessionsAndEmptyRunsAreNotEstimated() {
        assertThat(SessionKmEstimator.estimateKm(
                session(Discipline.GYM, null, 60, null, null), MODEL)).isNull();
        assertThat(SessionKmEstimator.estimateKm(
                session(Discipline.RUN, "EF", null, null, null), MODEL)).isNull();
    }

    @Test
    void inconsistentElevationNeverErasesMoreThanHalfTheDistance() {
        // 2000 m D+ in one easy hour is a data-entry mistake, not a prediction
        PlannedSession run = session(Discipline.RUN, "EF", 60, 2000,
                List.of(Node.step(Allure.EF, 3600, null)));

        assertThat(SessionKmEstimator.estimateKm(run, MODEL))
                .isEqualByComparingTo(new BigDecimal("5.0"));
    }
}
