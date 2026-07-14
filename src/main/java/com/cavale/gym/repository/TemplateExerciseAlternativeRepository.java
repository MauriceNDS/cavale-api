package com.cavale.gym.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cavale.gym.domain.TemplateExerciseAlternative;

public interface TemplateExerciseAlternativeRepository
        extends JpaRepository<TemplateExerciseAlternative, UUID> {

    List<TemplateExerciseAlternative> findByTemplateExerciseIdOrderByPositionAsc(UUID templateExerciseId);

    List<TemplateExerciseAlternative> findByTemplateExerciseIdInOrderByPositionAsc(
            Collection<UUID> templateExerciseIds);

    boolean existsByExerciseId(UUID exerciseId);

    boolean existsByTemplateExerciseIdAndExerciseId(UUID templateExerciseId, UUID exerciseId);
}
