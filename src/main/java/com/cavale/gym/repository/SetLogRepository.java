package com.cavale.gym.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.cavale.gym.domain.SetLog;

public interface SetLogRepository extends JpaRepository<SetLog, UUID> {

    List<SetLog> findByWorkoutLogIdOrderByPositionAscSetNumberAsc(UUID workoutLogId);

    List<SetLog> findByWorkoutLogIdIn(java.util.Collection<UUID> workoutLogIds);

    /** Every set of every finished workout, oldest first — the stats corpus. */
    List<SetLog> findByWorkoutLogUserIdAndWorkoutLogStatusOrderByWorkoutLogStartedAtAsc(
            UUID userId, com.cavale.gym.domain.WorkoutStatus status);

    Optional<SetLog> findByWorkoutLogIdAndExerciseIdAndSetNumber(UUID workoutLogId, UUID exerciseId,
                                                                 int setNumber);

    boolean existsByExerciseId(UUID exerciseId);

    List<SetLog> findByWorkoutLogIdAndExerciseId(UUID workoutLogId, UUID exerciseId);

    /** The sets of the LAST finished workout containing this exercise — the prefill. */
    @Query("""
            select s from SetLog s
            where s.exercise.id = :exerciseId
              and s.workoutLog.userId = :userId
              and s.workoutLog.status = com.cavale.gym.domain.WorkoutStatus.FINISHED
              and s.workoutLog.id = (
                  select s2.workoutLog.id from SetLog s2
                  where s2.exercise.id = :exerciseId
                    and s2.workoutLog.userId = :userId
                    and s2.workoutLog.status = com.cavale.gym.domain.WorkoutStatus.FINISHED
                  order by s2.workoutLog.startedAt desc
                  limit 1)
            order by s.setNumber asc
            """)
    List<SetLog> findLastWorkoutSets(@Param("userId") UUID userId,
                                     @Param("exerciseId") UUID exerciseId);

    /** Heaviest weight ever lifted at this exact rep count — the record. */
    @Query("""
            select max(s.weightKg) from SetLog s
            where s.exercise.id = :exerciseId
              and s.workoutLog.userId = :userId
              and s.workoutLog.status = com.cavale.gym.domain.WorkoutStatus.FINISHED
              and s.reps = :reps
            """)
    Optional<BigDecimal> findRecordWeight(@Param("userId") UUID userId,
                                          @Param("exerciseId") UUID exerciseId,
                                          @Param("reps") int reps);
}
