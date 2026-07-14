package com.cavale.gym.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.cavale.gym.domain.Equipment;
import com.cavale.gym.domain.Exercise;
import com.cavale.gym.domain.ExerciseCategory;
import com.cavale.gym.domain.ExerciseMeasure;
import com.cavale.gym.domain.Muscle;
import com.cavale.gym.domain.SetLog;
import com.cavale.gym.domain.WorkoutLog;
import com.cavale.gym.domain.WorkoutStatus;
import com.cavale.gym.dto.GymStatsResponse;
import com.cavale.gym.repository.SetLogRepository;
import com.cavale.training.repository.PlannedSessionRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GymStatsServiceTest {

    private static final UUID USER = UUID.randomUUID();
    /** A Monday. */
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 13);

    @Mock
    private SetLogRepository setLogRepository;

    @Mock
    private PlannedSessionRepository sessionRepository;

    private GymStatsService service() {
        return new GymStatsService(setLogRepository, sessionRepository);
    }

    private static Exercise squat() {
        Exercise exercise = new Exercise(USER, "Squat", ExerciseCategory.FORCE,
                Equipment.BARBELL, ExerciseMeasure.WEIGHT_REPS);
        exercise.updateMuscles(java.util.Set.of(Muscle.QUADRICEPS, Muscle.FESSIERS));
        ReflectionTestUtils.setField(exercise, "id", UUID.randomUUID());
        return exercise;
    }

    private static WorkoutLog logOn(LocalDate day) {
        WorkoutLog log = new WorkoutLog(USER, null, null, "Force Max · A",
                day.atTime(18, 0).atZone(ZoneId.systemDefault()).toInstant());
        ReflectionTestUtils.setField(log, "id", UUID.randomUUID());
        log.finish(50, null, false, null);
        return log;
    }

    private static SetLog set(WorkoutLog log, Exercise exercise, int setNumber, int reps, String kg) {
        return new SetLog(log, exercise, 0, setNumber, reps, new BigDecimal(kg), null);
    }

    @Test
    void epley_matchesTheFormula() {
        assertThat(GymStatsService.estimateOneRm(new BigDecimal("100"), 1))
                .isEqualByComparingTo("103.3"); // 100 × (1 + 1/30)
        assertThat(GymStatsService.estimateOneRm(new BigDecimal("85"), 6))
                .isEqualByComparingTo("102.0"); // 85 × 1.2
    }

    @Test
    void stats_computeTrendTonnagePrsAndBalance() {
        Exercise squat = squat();
        WorkoutLog twoWeeksAgo = logOn(TODAY.minusWeeks(2));
        WorkoutLog thisWeek = logOn(TODAY);
        when(setLogRepository
                .findByWorkoutLogUserIdAndWorkoutLogStatusOrderByWorkoutLogStartedAtAsc(
                        USER, WorkoutStatus.FINISHED))
                .thenReturn(List.of(
                        set(twoWeeksAgo, squat, 1, 6, "80.0"),
                        set(twoWeeksAgo, squat, 2, 6, "85.0"),
                        set(thisWeek, squat, 1, 6, "87.5")));
        when(sessionRepository.findByUserIdAndDateBetweenOrderByDateAscOrderInDayAsc(
                eq(USER), any(), any())).thenReturn(List.of());

        GymStatsResponse stats = service().getStats(USER, TODAY);

        // trend: one exercise, one point per training day, best 1RM of the day
        assertThat(stats.oneRmTrends()).hasSize(1);
        var trend = stats.oneRmTrends().getFirst();
        assertThat(trend.name()).isEqualTo("Squat");
        assertThat(trend.points()).hasSize(2);
        assertThat(trend.points().getFirst().estOneRmKg()).isEqualByComparingTo("102.0"); // 85×1.2
        assertThat(trend.points().getLast().estOneRmKg()).isEqualByComparingTo("105.0");  // 87.5×1.2

        // tonnage: current week = 87.5 × 6
        var currentWeek = stats.weeklyTonnage().getLast();
        assertThat(currentWeek.weekStart()).isEqualTo(TODAY);
        assertThat(currentWeek.tonnageKg()).isEqualByComparingTo("525");
        assertThat(currentWeek.workouts()).isEqualTo(1);

        // PR wall: 87.5 beat 85, set today
        assertThat(stats.prWall()).hasSize(1);
        var pr = stats.prWall().getFirst();
        assertThat(pr.weightKg()).isEqualByComparingTo("87.5");
        assertThat(pr.previousKg()).isEqualByComparingTo("85.0");
        assertThat(pr.date()).isEqualTo(TODAY);

        // balance: both muscles collect all 3 sets
        assertThat(stats.muscleVolume())
                .extracting(GymStatsResponse.MuscleVolume::muscle,
                        GymStatsResponse.MuscleVolume::sets)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(Muscle.QUADRICEPS, 3),
                        org.assertj.core.groups.Tuple.tuple(Muscle.FESSIERS, 3));
    }

    @Test
    void prWall_ignoresOldRecords() {
        Exercise squat = squat();
        WorkoutLog old = logOn(TODAY.minusDays(90));
        when(setLogRepository
                .findByWorkoutLogUserIdAndWorkoutLogStatusOrderByWorkoutLogStartedAtAsc(
                        USER, WorkoutStatus.FINISHED))
                .thenReturn(List.of(set(old, squat, 1, 6, "85.0")));
        when(sessionRepository.findByUserIdAndDateBetweenOrderByDateAscOrderInDayAsc(
                eq(USER), any(), any())).thenReturn(List.of());

        assertThat(service().getStats(USER, TODAY).prWall()).isEmpty();
    }
}
