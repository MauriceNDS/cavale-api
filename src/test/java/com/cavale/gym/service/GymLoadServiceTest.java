package com.cavale.gym.service;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.cavale.gym.domain.WorkoutLog;
import com.cavale.gym.domain.WorkoutStatus;
import com.cavale.training.domain.PerceivedEffort;

import static org.assertj.core.api.Assertions.assertThat;

class GymLoadServiceTest {

    private static WorkoutLog finished(int durationMin, PerceivedEffort effort) {
        WorkoutLog log = new WorkoutLog(UUID.randomUUID(), null, null, "Force", Instant.now());
        log.finish(durationMin, effort, false, null);
        return log;
    }

    @Test
    void aSessionLandsInTheSameBandRunsOccupy() {
        // real runs on this athlete produce 0.5–1.2 relative effort per minute
        assertThat(GymLoadService.loadOf(finished(39, PerceivedEffort.COMME_PREVU))).isEqualTo(27);
        assertThat(GymLoadService.loadOf(finished(60, PerceivedEffort.COMME_PREVU))).isEqualTo(42);

        // …and never leaves it, however the session felt
        for (PerceivedEffort effort : PerceivedEffort.values()) {
            double perMinute = GymLoadService.loadOf(finished(60, effort)) / 60.0;
            assertThat(perMinute).isBetween(0.35, 1.05);
        }
    }

    @Test
    void rawSessionRpeWouldSwampTheModel_whichIsWhyItIsNotUsed() {
        int ours = GymLoadService.loadOf(finished(39, PerceivedEffort.COMME_PREVU));
        int rawSrpe = 39 * 6; // RPE 6 × minutes, the textbook formula

        // a 39-minute leg day must not outweigh three long runs
        assertThat(ours).isLessThan(rawSrpe / 5);
    }

    @Test
    void harderFeelsCostMore_monotonically() {
        int previous = -1;
        for (PerceivedEffort effort : new PerceivedEffort[] {
                PerceivedEffort.TROP_FACILE, PerceivedEffort.FACILE, PerceivedEffort.COMME_PREVU,
                PerceivedEffort.DIFFICILE, PerceivedEffort.TROP_DIFFICILE }) {
            int load = GymLoadService.loadOf(finished(60, effort));
            assertThat(load).isGreaterThan(previous);
            previous = load;
        }
    }

    @Test
    void anUnfinishedWorkoutIsWorthNothingYet() {
        WorkoutLog running = new WorkoutLog(UUID.randomUUID(), null, null, "Force", Instant.now());
        assertThat(running.getStatus()).isEqualTo(WorkoutStatus.IN_PROGRESS);
        assertThat(GymLoadService.loadOf(running)).isZero();
    }

    @Test
    void anUnratedSessionIsReadAsPlanned() {
        WorkoutLog log = new WorkoutLog(UUID.randomUUID(), null, null, "Force", Instant.now());
        log.finish(40, null, false, null);
        ReflectionTestUtils.setField(log, "status", WorkoutStatus.FINISHED);

        // 0.70/min — the same rate the app already assumes for a run with no HR
        assertThat(GymLoadService.loadOf(log)).isEqualTo(28);
    }
}
