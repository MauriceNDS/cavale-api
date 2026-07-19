package com.cavale.gym.web;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.cavale.gym.domain.GymTemplate;
import com.cavale.gym.domain.GymTemplateVariant;
import com.cavale.gym.domain.TemplateExercise;
import com.cavale.gym.dto.TemplateDtos.AlternativeRequest;
import com.cavale.gym.dto.TemplateDtos.AlternativeResponse;
import com.cavale.gym.dto.TemplateDtos.ReorderRequest;
import com.cavale.gym.dto.TemplateDtos.TemplateExerciseRequest;
import com.cavale.gym.dto.TemplateDtos.TemplateExerciseResponse;
import com.cavale.gym.dto.TemplateDtos.TemplateRequest;
import com.cavale.gym.dto.TemplateDtos.TemplateResponse;
import com.cavale.gym.dto.TemplateDtos.VariantDetailResponse;
import com.cavale.gym.dto.TemplateDtos.VariantRequest;
import com.cavale.gym.dto.TemplateDtos.VariantSummary;
import com.cavale.gym.service.GymTemplateService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/gym")
@Tag(name = "Gym templates", description = "Strength programs: variants, exercises, alternatives")
public class GymTemplateController {

    private final GymTemplateService templateService;

    public GymTemplateController(GymTemplateService templateService) {
        this.templateService = templateService;
    }

    /* ── Templates ────────────────────────────────────────────────────── */

    @GetMapping("/templates")
    @Operation(summary = "All programs with their variant summaries")
    public List<TemplateResponse> listTemplates(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = userId(jwt);
        return templateService.listTemplates(userId).stream()
                .map(template -> toResponse(userId, template))
                .toList();
    }

    @GetMapping("/templates/{templateId}")
    @Operation(summary = "One program with its variant summaries")
    public TemplateResponse getTemplate(@AuthenticationPrincipal Jwt jwt,
                                        @PathVariable UUID templateId) {
        UUID userId = userId(jwt);
        return toResponse(userId, templateService.getOwnedTemplate(userId, templateId));
    }

    @PostMapping("/templates")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a program (born with variant A)")
    public TemplateResponse createTemplate(@AuthenticationPrincipal Jwt jwt,
                                           @Valid @RequestBody TemplateRequest request) {
        UUID userId = userId(jwt);
        return toResponse(userId, templateService.createTemplate(userId, request));
    }

    @PatchMapping("/templates/{templateId}")
    @Operation(summary = "Rename a program, update its goal or archive it")
    public TemplateResponse updateTemplate(@AuthenticationPrincipal Jwt jwt,
                                           @PathVariable UUID templateId,
                                           @Valid @RequestBody TemplateRequest request) {
        UUID userId = userId(jwt);
        return toResponse(userId, templateService.updateTemplate(userId, templateId, request));
    }

    @DeleteMapping("/templates/{templateId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a program and all its content")
    public void deleteTemplate(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID templateId) {
        templateService.deleteTemplate(userId(jwt), templateId);
    }

    /* ── Variants ─────────────────────────────────────────────────────── */

    @GetMapping("/variants/{variantId}")
    @Operation(summary = "One variant with its ordered exercises and their alternatives")
    public VariantDetailResponse getVariant(@AuthenticationPrincipal Jwt jwt,
                                            @PathVariable UUID variantId) {
        // assembled inside the service transaction — lazy proxies die at its edge
        return templateService.getVariantDetail(userId(jwt), variantId);
    }

    @PostMapping("/templates/{templateId}/variants")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add an empty variant (B, C…)")
    public VariantSummary addVariant(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID templateId,
                                     @Valid @RequestBody VariantRequest request) {
        return VariantSummary.from(templateService.addVariant(userId(jwt), templateId, request), 0);
    }

    @PostMapping("/variants/{variantId}/copy")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Duplicate a variant with all its content — how B is born from A")
    public VariantSummary copyVariant(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID variantId,
                                      @Valid @RequestBody VariantRequest request) {
        UUID userId = userId(jwt);
        GymTemplateVariant copy = templateService.copyVariant(userId, variantId, request);
        return VariantSummary.from(copy, templateService.getExercises(userId, copy.getId()).size());
    }

    @PatchMapping("/variants/{variantId}")
    @Operation(summary = "Rename a variant or update its note")
    public VariantSummary updateVariant(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID variantId,
                                        @Valid @RequestBody VariantRequest request) {
        UUID userId = userId(jwt);
        GymTemplateVariant variant = templateService.updateVariant(userId, variantId, request);
        return VariantSummary.from(variant, templateService.getExercises(userId, variantId).size());
    }

    @DeleteMapping("/variants/{variantId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a variant (a program keeps at least one)")
    public void deleteVariant(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID variantId) {
        templateService.deleteVariant(userId(jwt), variantId);
    }

    /* ── Prescriptions & alternatives ─────────────────────────────────── */

    @PostMapping("/variants/{variantId}/exercises")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Append an exercise prescription to a variant")
    public TemplateExerciseResponse addExercise(@AuthenticationPrincipal Jwt jwt,
                                                @PathVariable UUID variantId,
                                                @Valid @RequestBody TemplateExerciseRequest request) {
        return TemplateExerciseResponse.from(
                templateService.addExercise(userId(jwt), variantId, request), List.of());
    }

    @PatchMapping("/template-exercises/{templateExerciseId}")
    @Operation(summary = "Update a prescription (exercise, sets/reps/seconds, rest, intensity, note)")
    public TemplateExerciseResponse updateExercise(@AuthenticationPrincipal Jwt jwt,
                                                   @PathVariable UUID templateExerciseId,
                                                   @Valid @RequestBody TemplateExerciseRequest request) {
        // the swapped-in exercise was loaded in the same call — safe to map;
        // alternatives are not (lazy proxies), the client refetches the variant
        TemplateExercise te = templateService.updateExercise(userId(jwt), templateExerciseId, request);
        return TemplateExerciseResponse.from(te, List.of());
    }

    @DeleteMapping("/template-exercises/{templateExerciseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a prescription from its variant")
    public void removeExercise(@AuthenticationPrincipal Jwt jwt,
                               @PathVariable UUID templateExerciseId) {
        templateService.removeExercise(userId(jwt), templateExerciseId);
    }

    @PutMapping("/variants/{variantId}/exercises/order")
    @Operation(summary = "Reorder a variant's exercises (send every id, in the new order)")
    public List<TemplateExerciseResponse> reorder(@AuthenticationPrincipal Jwt jwt,
                                                  @PathVariable UUID variantId,
                                                  @Valid @RequestBody ReorderRequest request) {
        // assembled inside the service transaction — lazy proxies die at its edge
        return templateService.reorderExercises(userId(jwt), variantId, request);
    }

    @PostMapping("/template-exercises/{templateExerciseId}/alternatives")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a fallback exercise (machine busy / no gym)")
    public AlternativeResponse addAlternative(@AuthenticationPrincipal Jwt jwt,
                                              @PathVariable UUID templateExerciseId,
                                              @Valid @RequestBody AlternativeRequest request) {
        return AlternativeResponse.from(
                templateService.addAlternative(userId(jwt), templateExerciseId, request.exerciseId()));
    }

    @DeleteMapping("/alternatives/{alternativeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a fallback")
    public void removeAlternative(@AuthenticationPrincipal Jwt jwt,
                                  @PathVariable UUID alternativeId) {
        templateService.removeAlternative(userId(jwt), alternativeId);
    }

    /* ── Internals ────────────────────────────────────────────────────── */

    private TemplateResponse toResponse(UUID userId, GymTemplate template) {
        Map<UUID, Long> counts = templateService.getVariants(userId, template.getId()).stream()
                .collect(Collectors.toMap(GymTemplateVariant::getId,
                        v -> (long) templateService.getExercises(userId, v.getId()).size()));
        List<VariantSummary> variants = templateService.getVariants(userId, template.getId()).stream()
                .map(v -> VariantSummary.from(v, counts.getOrDefault(v.getId(), 0L)))
                .toList();
        return TemplateResponse.from(template, variants);
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
