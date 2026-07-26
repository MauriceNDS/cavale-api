package com.cavale.training.pace;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cavale.training.domain.Objective;
import com.cavale.training.domain.ObjectiveKind;
import com.cavale.training.domain.ObjectiveRole;
import com.cavale.training.domain.PlanStatus;
import com.cavale.training.domain.TrainingPlan;
import com.cavale.training.dto.PaceContextResponse;
import com.cavale.training.repository.ObjectiveRepository;
import com.cavale.training.repository.TrainingPlanRepository;

/**
 * The athlete's current paces, packaged for display: the fitted
 * {@link PaceModel} plus the season context that decides HOW the web shows it
 * — pace bands only make sense on a ROAD season (trail thinks in km-effort),
 * and a road race with a target time anchors a goal pace on top of the model.
 * Paces are always derived at display time, never stored on sessions, so the
 * same plan prescribes faster bands as fitness improves.
 */
@Service
public class PaceContextService {

    private final PaceModelService paceModelService;
    private final TrainingPlanRepository planRepository;
    private final ObjectiveRepository objectiveRepository;

    public PaceContextService(PaceModelService paceModelService,
                              TrainingPlanRepository planRepository,
                              ObjectiveRepository objectiveRepository) {
        this.paceModelService = paceModelService;
        this.planRepository = planRepository;
        this.objectiveRepository = objectiveRepository;
    }

    @Transactional(readOnly = true)
    public PaceContextResponse contextFor(UUID userId, LocalDate today) {
        PaceModel model = paceModelService.modelFor(userId);
        Objective main = currentMainObjective(userId, today);
        boolean road = main != null && main.getKind() == ObjectiveKind.ROAD;
        return PaceContextResponse.of(model, road, road ? goalPaceSecPerKm(main) : null);
    }

    /** MAIN objective of the ACTIVE season (else the season covering today). */
    private Objective currentMainObjective(UUID userId, LocalDate today) {
        List<TrainingPlan> plans = planRepository.findByUserIdOrderByStartDateDesc(userId);
        TrainingPlan current = plans.stream()
                .filter(p -> p.getStatus() == PlanStatus.ACTIVE)
                .findFirst()
                .orElseGet(() -> plans.stream()
                        .filter(p -> !today.isBefore(p.getStartDate()) && !today.isAfter(p.getEndDate()))
                        .findFirst()
                        .orElse(null));
        if (current == null) {
            return null;
        }
        return objectiveRepository.findByPlanIdAndRole(current.getId(), ObjectiveRole.MAIN)
                .orElse(null);
    }

    private static Integer goalPaceSecPerKm(Objective main) {
        if (main.getTargetTimeMin() == null || main.getDistanceKm() == null
                || main.getDistanceKm().doubleValue() <= 0) {
            return null;
        }
        return (int) Math.round(main.getTargetTimeMin() * 60 / main.getDistanceKm().doubleValue());
    }
}
