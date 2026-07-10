package com.cavale.training.web;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cavale.integration.strava.StravaActivityService;
import com.cavale.training.domain.Activity;
import com.cavale.training.dto.SessionResponse;
import com.cavale.training.dto.UpdateSessionRequest;
import com.cavale.training.dto.ValidateSessionRequest;
import com.cavale.training.service.TrainingPlanService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/sessions")
@Tag(name = "Sessions", description = "Planned session updates")
public class PlannedSessionController {

    private final TrainingPlanService planService;
    private final StravaActivityService stravaActivityService;

    public PlannedSessionController(TrainingPlanService planService,
                                    StravaActivityService stravaActivityService) {
        this.planService = planService;
        this.stravaActivityService = stravaActivityService;
    }

    public record ImportStravaRequest(long stravaActivityId) {
    }

    @PatchMapping("/{sessionId}")
    @Operation(summary = "Move a session (date/order) and/or update its status")
    public SessionResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID sessionId,
                                  @Valid @RequestBody UpdateSessionRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return SessionResponse.from(planService.updateSession(userId, sessionId, request));
    }

    @PostMapping("/{sessionId}/validate")
    @Operation(summary = "Validate a running session with actual measures (time + distance required)")
    public SessionResponse validate(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID sessionId,
                                    @Valid @RequestBody ValidateSessionRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        Activity activity = planService.validateSession(userId, sessionId, request);
        return SessionResponse.from(activity.getSession(), activity);
    }

    @PostMapping("/{sessionId}/validate-strava")
    @Operation(summary = "Validate a running session by attaching a Strava activity")
    public SessionResponse validateFromStrava(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID sessionId,
                                              @RequestBody ImportStravaRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        Activity activity = stravaActivityService.importToSession(userId, sessionId, request.stravaActivityId());
        return SessionResponse.from(activity.getSession(), activity);
    }
}
