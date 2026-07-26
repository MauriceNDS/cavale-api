package com.cavale.gym.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cavale.common.exception.ResourceNotFoundException;
import com.cavale.gym.domain.Exercise;
import com.cavale.gym.dto.ExerciseRequest;
import com.cavale.gym.repository.ExerciseRepository;
import com.cavale.gym.repository.SetLogRepository;
import com.cavale.gym.repository.TemplateExerciseAlternativeRepository;
import com.cavale.gym.repository.TemplateExerciseRepository;

/**
 * The exercise library. Names are unique per athlete (case-insensitive) —
 * the duplicate guard lives HERE so every front door (REST today, MCP
 * tomorrow) gets it for free. Deleting is only allowed while nothing
 * references the exercise; otherwise archive it.
 */
@Service
public class ExerciseService {

    private final ExerciseRepository exerciseRepository;
    private final TemplateExerciseRepository templateExerciseRepository;
    private final TemplateExerciseAlternativeRepository alternativeRepository;
    private final SetLogRepository setLogRepository;

    public ExerciseService(ExerciseRepository exerciseRepository,
                           TemplateExerciseRepository templateExerciseRepository,
                           TemplateExerciseAlternativeRepository alternativeRepository,
                           SetLogRepository setLogRepository) {
        this.exerciseRepository = exerciseRepository;
        this.templateExerciseRepository = templateExerciseRepository;
        this.alternativeRepository = alternativeRepository;
        this.setLogRepository = setLogRepository;
    }

    @Transactional(readOnly = true)
    public List<Exercise> list(UUID userId) {
        return exerciseRepository.findByUserIdOrderByNameAsc(userId);
    }

    @Transactional(readOnly = true)
    public Exercise getOwned(UUID userId, UUID exerciseId) {
        return exerciseRepository.findById(exerciseId)
                .filter(e -> e.getUserId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Exercise", exerciseId));
    }

    @Transactional
    public Exercise create(UUID userId, ExerciseRequest request) {
        String name = request.name().trim();
        if (exerciseRepository.existsByUserIdAndNameIgnoreCase(userId, name)) {
            throw new IllegalArgumentException(
                    "Un exercice nommé « " + name + " » existe déjà dans ta bibliothèque");
        }
        Exercise exercise = new Exercise(userId, name, request.category(),
                request.equipment(), request.measure());
        exercise.updateTheory(trimmed(request.description()), trimmed(request.resourceUrl()),
                trimmed(request.runningBenefit()));
        exercise.updateMuscles(request.muscles());
        exercise.updateLoading(request.incrementKg(), request.referenceWeightKg());
        if (request.derivedFromId() != null) {
            exercise.deriveFrom(getOwned(userId, request.derivedFromId()));
        }
        return exerciseRepository.save(exercise);
    }

    @Transactional
    public Exercise update(UUID userId, UUID exerciseId, ExerciseRequest request) {
        Exercise exercise = getOwned(userId, exerciseId);
        String name = request.name().trim();
        if (!exercise.getName().equalsIgnoreCase(name)
                && exerciseRepository.existsByUserIdAndNameIgnoreCase(userId, name)) {
            throw new IllegalArgumentException(
                    "Un exercice nommé « " + name + " » existe déjà dans ta bibliothèque");
        }
        exercise.updateBasics(name, request.category(), request.equipment(), request.measure());
        exercise.updateTheory(trimmed(request.description()), trimmed(request.resourceUrl()),
                trimmed(request.runningBenefit()));
        exercise.updateMuscles(request.muscles());
        exercise.updateLoading(request.incrementKg(), request.referenceWeightKg());
        if (request.archived() != null) {
            exercise.updateArchived(request.archived());
        }
        return exercise;
    }

    @Transactional
    public void delete(UUID userId, UUID exerciseId) {
        Exercise exercise = getOwned(userId, exerciseId);
        if (templateExerciseRepository.existsByExerciseId(exerciseId)
                || alternativeRepository.existsByExerciseId(exerciseId)) {
            throw new IllegalArgumentException(
                    "Cet exercice est utilisé par un programme — archive-le plutôt");
        }
        if (setLogRepository.existsByExerciseId(exerciseId)) {
            throw new IllegalArgumentException(
                    "Des séries enregistrées référencent cet exercice — archive-le plutôt");
        }
        if (exerciseRepository.existsByDerivedFromId(exerciseId)) {
            throw new IllegalArgumentException(
                    "Des exercices dérivent de celui-ci — archive-le plutôt");
        }
        exerciseRepository.delete(exercise);
    }

    private static String trimmed(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
