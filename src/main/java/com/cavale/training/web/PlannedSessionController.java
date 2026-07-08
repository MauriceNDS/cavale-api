package com.cavale.training.web;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cavale.training.dto.SessionResponse;
import com.cavale.training.dto.UpdateSessionRequest;
import com.cavale.training.service.TrainingPlanService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/sessions")
@Tag(name = "Sessions", description = "Planned session updates")
public class PlannedSessionController {

    private final TrainingPlanService planService;

    public PlannedSessionController(TrainingPlanService planService) {
        this.planService = planService;
    }

    @PatchMapping("/{sessionId}")
    @Operation(summary = "Move a session (date/order) and/or update its status")
    public SessionResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID sessionId,
                                  @Valid @RequestBody UpdateSessionRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return SessionResponse.from(planService.updateSession(userId, sessionId, request));
    }
}
