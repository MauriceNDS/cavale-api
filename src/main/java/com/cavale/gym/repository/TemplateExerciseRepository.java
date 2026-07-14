package com.cavale.gym.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cavale.gym.domain.TemplateExercise;

public interface TemplateExerciseRepository extends JpaRepository<TemplateExercise, UUID> {

    List<TemplateExercise> findByVariantIdOrderByPositionAsc(UUID variantId);

    List<TemplateExercise> findByVariantIdInOrderByPositionAsc(Collection<UUID> variantIds);

    boolean existsByExerciseId(UUID exerciseId);

    long countByVariantId(UUID variantId);
}
