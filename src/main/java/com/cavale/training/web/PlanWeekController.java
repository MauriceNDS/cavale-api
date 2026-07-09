package com.cavale.training.web;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.cavale.training.dto.CreateSessionRequest;
import com.cavale.training.dto.SessionResponse;
import com.cavale.training.dto.UpdateWeekRequest;
import com.cavale.training.dto.WeekResponse;
import com.cavale.training.service.TrainingPlanService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/weeks")
@Tag(name = "Plan weeks", description = "Sessions within a plan week")
public class PlanWeekController {

    private final TrainingPlanService planService;

    public PlanWeekController(TrainingPlanService planService) {
        this.planService = planService;
    }

    @PostMapping("/{weekId}/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a planned session to a week")
    public SessionResponse addSession(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID weekId,
                                      @Valid @RequestBody CreateSessionRequest request) {
        return SessionResponse.from(planService.addSession(userId(jwt), weekId, request));
    }

    @GetMapping("/{weekId}/sessions")
    @Operation(summary = "List a week's sessions")
    public List<SessionResponse> sessions(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID weekId) {
        return planService.getWeekSessions(userId(jwt), weekId).stream().map(SessionResponse::from).toList();
    }

    @PatchMapping("/{weekId}")
    @Operation(summary = "Update a week's description")
    public WeekResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID weekId,
                               @RequestBody UpdateWeekRequest request) {
        return WeekResponse.from(planService.updateWeek(userId(jwt), weekId, request));
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
