package com.cavale.training.web;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import com.cavale.training.domain.TrainingPlan;
import com.cavale.training.dto.CreatePlanRequest;
import com.cavale.training.dto.CreateWeekRequest;
import com.cavale.training.dto.ImportResult;
import com.cavale.training.dto.PlanDetailResponse;
import com.cavale.training.dto.PlanResponse;
import com.cavale.training.dto.WeekResponse;
import com.cavale.training.service.PlanImportException;
import com.cavale.training.service.PlanImportService;
import com.cavale.training.service.TrainingPlanService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/plans")
@Tag(name = "Training plans", description = "Plans, weeks, and their structure")
public class TrainingPlanController {

    private final TrainingPlanService planService;
    private final PlanImportService importService;

    public TrainingPlanController(TrainingPlanService planService, PlanImportService importService) {
        this.planService = planService;
        this.importService = importService;
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

    @DeleteMapping("/{planId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a plan and everything in it")
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID planId) {
        planService.deletePlan(userId(jwt), planId);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Import a full plan from the canonical CSV format (docs/IMPORT-FORMAT.md)")
    public ImportResult importPlan(@AuthenticationPrincipal Jwt jwt,
                                   @RequestParam("file") MultipartFile file) {
        try (InputStreamReader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)) {
            return importService.importPlan(userId(jwt), reader);
        } catch (IOException e) {
            throw new PlanImportException("could not read the uploaded file: " + e.getMessage());
        }
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
