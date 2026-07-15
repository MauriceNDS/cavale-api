package com.cavale.training.dto;

import java.util.List;

/**
 * The structural verdict on a generated plan: valid when the issues list is
 * empty. Each issue is a plain-language problem the coach should fix (an empty
 * training week, an unparseable session, two hard days back-to-back, a missing
 * taper or deload…).
 */
public record PlanValidationResponse(boolean valid, List<String> issues) {

    public static PlanValidationResponse of(List<String> issues) {
        return new PlanValidationResponse(issues.isEmpty(), issues);
    }
}
