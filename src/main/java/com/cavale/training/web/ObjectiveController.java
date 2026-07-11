package com.cavale.training.web;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.cavale.training.dto.CreateObjectiveRequest;
import com.cavale.training.dto.ObjectiveResponse;
import com.cavale.training.dto.PlanProgressResponse;
import com.cavale.training.dto.UpdateObjectiveRequest;
import com.cavale.training.service.ObjectiveService;
import com.cavale.training.service.PlanProgressService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
@Tag(name = "Objectives", description = "What a plan is built around, and progress towards it")
public class ObjectiveController {

    private final ObjectiveService objectiveService;
    private final PlanProgressService progressService;

    public ObjectiveController(ObjectiveService objectiveService, PlanProgressService progressService) {
        this.objectiveService = objectiveService;
        this.progressService = progressService;
    }

    @GetMapping("/plans/{planId}/objectives")
    @Operation(summary = "List a plan's objectives (main first)")
    public List<ObjectiveResponse> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID planId) {
        return objectiveService.listForPlan(userId(jwt), planId).stream()
                .map(ObjectiveResponse::from)
                .toList();
    }

    @PostMapping("/plans/{planId}/objectives")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a secondary objective to a plan")
    public ObjectiveResponse create(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID planId,
                                    @Valid @RequestBody CreateObjectiveRequest request) {
        return ObjectiveResponse.from(objectiveService.addSecondary(userId(jwt), planId, request));
    }

    @PutMapping("/objectives/{objectiveId}")
    @Operation(summary = "Replace an objective's editable fields")
    public ObjectiveResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID objectiveId,
                                    @Valid @RequestBody UpdateObjectiveRequest request) {
        return ObjectiveResponse.from(objectiveService.update(userId(jwt), objectiveId, request));
    }

    @DeleteMapping("/objectives/{objectiveId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a secondary objective")
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID objectiveId) {
        objectiveService.delete(userId(jwt), objectiveId);
    }

    @GetMapping("/plans/{planId}/progress")
    @Operation(summary = "Progress towards the plan's objectives (objective page data)")
    public PlanProgressResponse progress(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID planId) {
        return progressService.getProgress(userId(jwt), planId);
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
