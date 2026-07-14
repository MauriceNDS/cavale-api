package com.cavale.gym.dto;

import java.util.Set;
import java.util.UUID;

import com.cavale.gym.domain.Equipment;
import com.cavale.gym.domain.Exercise;
import com.cavale.gym.domain.ExerciseCategory;
import com.cavale.gym.domain.ExerciseMeasure;
import com.cavale.gym.domain.Muscle;

public record ExerciseResponse(
        UUID id,
        String name,
        ExerciseCategory category,
        Equipment equipment,
        ExerciseMeasure measure,
        String description,
        String resourceUrl,
        String runningBenefit,
        Set<Muscle> muscles,
        UUID derivedFromId,
        String derivedFromName,
        boolean archived) {

    public static ExerciseResponse from(Exercise exercise) {
        Exercise parent = exercise.getDerivedFrom();
        return new ExerciseResponse(exercise.getId(), exercise.getName(), exercise.getCategory(),
                exercise.getEquipment(), exercise.getMeasure(), exercise.getDescription(),
                exercise.getResourceUrl(), exercise.getRunningBenefit(), exercise.getMuscles(),
                parent != null ? parent.getId() : null,
                parent != null ? parent.getName() : null,
                exercise.isArchived());
    }
}
