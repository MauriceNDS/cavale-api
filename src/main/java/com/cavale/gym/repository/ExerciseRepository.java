package com.cavale.gym.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.cavale.gym.domain.Exercise;

public interface ExerciseRepository extends JpaRepository<Exercise, UUID> {

    /**
     * derivedFrom is fetched along (here and in findById): ExerciseResponse
     * needs the parent's name, and callers map the DTO after the service
     * transaction closed — an uninitialized parent proxy would blow up there.
     * LOAD, not the default FETCH: a fetchgraph would flip the otherwise-eager
     * muscles collection to lazy and break serialization the same way.
     */
    @EntityGraph(attributePaths = "derivedFrom", type = EntityGraph.EntityGraphType.LOAD)
    List<Exercise> findByUserIdOrderByNameAsc(UUID userId);

    @Override
    @EntityGraph(attributePaths = "derivedFrom", type = EntityGraph.EntityGraphType.LOAD)
    Optional<Exercise> findById(UUID id);

    /** Duplicate guard — one "Squat" per athlete, case-insensitive. */
    boolean existsByUserIdAndNameIgnoreCase(UUID userId, String name);

    boolean existsByDerivedFromId(UUID exerciseId);
}
