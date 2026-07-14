package com.cavale.gym.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cavale.gym.domain.Exercise;

public interface ExerciseRepository extends JpaRepository<Exercise, UUID> {

    List<Exercise> findByUserIdOrderByNameAsc(UUID userId);

    /** Duplicate guard — one "Squat" per athlete, case-insensitive. */
    boolean existsByUserIdAndNameIgnoreCase(UUID userId, String name);

    boolean existsByDerivedFromId(UUID exerciseId);
}
