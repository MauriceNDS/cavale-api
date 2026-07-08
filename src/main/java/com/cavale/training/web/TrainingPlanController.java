package com.cavale.training.web;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import com.cavale.training.domain.TrainingPlan;
import com.cavale.training.dto.CreatePlanRequest;
import com.cavale.training.dto.CreateWeekRequest;
import com.cavale.training.dto.PlanDetailResponse;
import com.cavale.training.dto.PlanResponse;
import com.cavale.training.dto.WeekResponse;
import com.cavale.training.service.TrainingPlanService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/plans")
@Tag(name = "Training plans", description = "Plans, weeks, and their structure")
public class TrainingPlanController {

    private final TrainingPlanService planService;

    public TrainingPlanController(TrainingPlanService planService) {
        this.planService = planService;
    }

    @PostMapping
    @Operation(summary = "Create a training plan")
    public ResponseEntity<PlanResponse> create(@AuthenticationPrincipal Jwt jwt,
                                               @Valid @RequestBody CreatePlanRequest request,
                                               UriComponentsBuilder uriBuilder) {
        TrainingPlan plan = planService.createPlan(userId(jwt), request);
        URI location = uriBuilder.path("/api/plans/{id}").buildAndExpand(plan.getId()).toUri();
        return ResponseEntity.created(location).body(PlanResponse.from(plan));
    }

    @GetMapping
    @Operation(summary = "List my training plans")
    public List<PlanResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return planService.listPlans(userId(jwt)).stream().map(PlanResponse::from).toList();
    }

    @GetMapping("/{planId}")
    @Operation(summary = "Get a plan with its weeks")
    public PlanDetailResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID planId) {
        UUID userId = userId(jwt);
        TrainingPlan plan = planService.getOwnedPlan(userId, planId);
        return PlanDetailResponse.from(plan, planService.getWeeks(userId, planId));
    }

    @PostMapping("/{planId}/weeks")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a week to a plan")
    public WeekResponse addWeek(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID planId,
                                @Valid @RequestBody CreateWeekRequest request) {
        return WeekResponse.from(planService.addWeek(userId(jwt), planId, request));
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
