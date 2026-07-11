package com.cavale.training.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.cavale.training.domain.Objective;
import com.cavale.training.domain.ObjectiveRole;
import com.cavale.training.domain.ObjectiveType;

public record ObjectiveResponse(
        UUID id,
        UUID planId,
        ObjectiveRole role,
        ObjectiveType type,
        String name,
        LocalDate date,
        BigDecimal distanceKm,
        Integer elevationGainM,
        Integer targetTimeMin,
        Integer resultTimeMin,
        String location,
        String notes) {

    public static ObjectiveResponse from(Objective objective) {
        return new ObjectiveResponse(objective.getId(), objective.getPlan().getId(), objective.getRole(),
                objective.getType(), objective.getName(), objective.getDate(), objective.getDistanceKm(),
                objective.getElevationGainM(), objective.getTargetTimeMin(), objective.getResultTimeMin(),
                objective.getLocation(), objective.getNotes());
    }
}
