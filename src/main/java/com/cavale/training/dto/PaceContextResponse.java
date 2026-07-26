package com.cavale.training.dto;

import java.util.Map;

import com.cavale.training.pace.PaceModel;
import com.cavale.training.workout.WorkoutStructure.Allure;

/**
 * The athlete's derived paces for display. {@code roadContext} gates the pace
 * bands in the UI: on a trail season the athlete thinks in km-effort, not
 * min/km. {@code goalPaceSecPerKm} is the road-race anchor (target time over
 * distance) — race-pace blocks show it instead of the model's COURSE pace.
 */
public record PaceContextResponse(
        Map<Allure, Integer> flatSecPerKm,
        double climbSecPerMeter,
        int sampleSize,
        boolean personal,
        boolean roadContext,
        Integer goalPaceSecPerKm) {

    public static PaceContextResponse of(PaceModel model, boolean roadContext, Integer goalPaceSecPerKm) {
        return new PaceContextResponse(model.flatSecPerKm(), model.climbSecPerMeter(),
                model.sampleSize(), model.personal(), roadContext, goalPaceSecPerKm);
    }
}
