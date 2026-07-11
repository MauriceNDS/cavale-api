package com.cavale.training.service;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cavale.common.exception.ResourceNotFoundException;
import com.cavale.training.domain.Objective;
import com.cavale.training.domain.ObjectiveRole;
import com.cavale.training.domain.TrainingPlan;
import com.cavale.training.dto.CreateObjectiveRequest;
import com.cavale.training.dto.UpdateObjectiveRequest;
import com.cavale.training.repository.ObjectiveRepository;

/**
 * Objectives of a plan: one MAIN (the season's reason to exist, created with
 * the plan, never deleted on its own) + any number of SECONDARY ones.
 */
@Service
public class ObjectiveService {

    private final ObjectiveRepository objectiveRepository;
    private final TrainingPlanService planService;

    public ObjectiveService(ObjectiveRepository objectiveRepository, TrainingPlanService planService) {
        this.objectiveRepository = objectiveRepository;
        this.planService = planService;
    }

    /** MAIN first, then secondaries by date (undated ones last). */
    @Transactional(readOnly = true)
    public List<Objective> listForPlan(UUID userId, UUID planId) {
        planService.getOwnedPlan(userId, planId);
        return sorted(objectiveRepository.findByPlanId(planId));
    }

    @Transactional
    public Objective addSecondary(UUID userId, UUID planId, CreateObjectiveRequest request) {
        TrainingPlan plan = planService.getOwnedPlan(userId, planId);
        Objective objective = new Objective(plan, ObjectiveRole.SECONDARY, request.type(),
                request.name().trim(), request.date());
        objective.updateRaceProfile(request.distanceKm(), request.elevationGainM(), trimmed(request.location()));
        objective.updateTargetTimeMin(request.targetTimeMin());
        objective.updateNotes(trimmed(request.notes()));
        return objectiveRepository.save(objective);
    }

    @Transactional
    public Objective update(UUID userId, UUID objectiveId, UpdateObjectiveRequest request) {
        Objective objective = getOwned(userId, objectiveId);
        objective.updateType(request.type());
        objective.rename(request.name().trim());
        objective.updateDate(request.date());
        objective.updateRaceProfile(request.distanceKm(), request.elevationGainM(), trimmed(request.location()));
        objective.updateTargetTimeMin(request.targetTimeMin());
        objective.recordResult(request.resultTimeMin());
        objective.updateNotes(trimmed(request.notes()));
        return objective;
    }

    @Transactional
    public void delete(UUID userId, UUID objectiveId) {
        Objective objective = getOwned(userId, objectiveId);
        if (objective.getRole() == ObjectiveRole.MAIN) {
            throw new IllegalArgumentException(
                    "A plan keeps its main objective — edit it, or delete the whole plan");
        }
        objectiveRepository.delete(objective);
    }

    static List<Objective> sorted(List<Objective> objectives) {
        return objectives.stream()
                .sorted(Comparator
                        .comparing((Objective o) -> o.getRole() != ObjectiveRole.MAIN)
                        .thenComparing(Objective::getDate,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private Objective getOwned(UUID userId, UUID objectiveId) {
        return objectiveRepository.findById(objectiveId)
                .filter(o -> o.getUserId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Objective", objectiveId));
    }

    private static String trimmed(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
