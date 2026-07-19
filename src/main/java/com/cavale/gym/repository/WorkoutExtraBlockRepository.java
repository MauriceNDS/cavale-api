package com.cavale.gym.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cavale.gym.domain.WorkoutExtraBlock;

public interface WorkoutExtraBlockRepository extends JpaRepository<WorkoutExtraBlock, UUID> {

    List<WorkoutExtraBlock> findByWorkoutLogIdOrderByPositionAsc(UUID workoutLogId);

    boolean existsByWorkoutLogIdAndExerciseId(UUID workoutLogId, UUID exerciseId);

    long countByWorkoutLogId(UUID workoutLogId);
}
