package com.cavale.gym.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cavale.gym.domain.WorkoutBlockOverride;

public interface WorkoutBlockOverrideRepository extends JpaRepository<WorkoutBlockOverride, UUID> {

    List<WorkoutBlockOverride> findByWorkoutLogId(UUID workoutLogId);

    Optional<WorkoutBlockOverride> findByWorkoutLogIdAndTemplateExerciseId(UUID workoutLogId,
                                                                           UUID templateExerciseId);
}
