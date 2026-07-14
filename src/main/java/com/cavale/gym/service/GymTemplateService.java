package com.cavale.gym.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cavale.common.exception.ResourceNotFoundException;
import com.cavale.gym.domain.Exercise;
import com.cavale.gym.domain.ExerciseMeasure;
import com.cavale.gym.domain.GymTemplate;
import com.cavale.gym.domain.GymTemplateVariant;
import com.cavale.gym.domain.TemplateExercise;
import com.cavale.gym.domain.TemplateExerciseAlternative;
import com.cavale.gym.dto.TemplateDtos.AlternativeResponse;
import com.cavale.gym.dto.TemplateDtos.ReorderRequest;
import com.cavale.gym.dto.TemplateDtos.TemplateExerciseRequest;
import com.cavale.gym.dto.TemplateDtos.TemplateExerciseResponse;
import com.cavale.gym.dto.TemplateDtos.TemplateRequest;
import com.cavale.gym.dto.TemplateDtos.VariantDetailResponse;
import com.cavale.gym.dto.TemplateDtos.VariantRequest;
import com.cavale.gym.repository.GymTemplateRepository;
import com.cavale.gym.repository.GymTemplateVariantRepository;
import com.cavale.gym.repository.TemplateExerciseAlternativeRepository;
import com.cavale.gym.repository.TemplateExerciseRepository;

/**
 * Strength programs: template → variants (A/B/C) → ordered prescriptions
 * with alternatives. A template is born with variant A — a program always
 * has at least one face — and duplicate names are rejected here so MCP
 * creation can't flood the library either.
 */
@Service
public class GymTemplateService {

    private final GymTemplateRepository templateRepository;
    private final GymTemplateVariantRepository variantRepository;
    private final TemplateExerciseRepository templateExerciseRepository;
    private final TemplateExerciseAlternativeRepository alternativeRepository;
    private final ExerciseService exerciseService;

    public GymTemplateService(GymTemplateRepository templateRepository,
                              GymTemplateVariantRepository variantRepository,
                              TemplateExerciseRepository templateExerciseRepository,
                              TemplateExerciseAlternativeRepository alternativeRepository,
                              ExerciseService exerciseService) {
        this.templateRepository = templateRepository;
        this.variantRepository = variantRepository;
        this.templateExerciseRepository = templateExerciseRepository;
        this.alternativeRepository = alternativeRepository;
        this.exerciseService = exerciseService;
    }

    /* ── Templates ────────────────────────────────────────────────────── */

    @Transactional(readOnly = true)
    public List<GymTemplate> listTemplates(UUID userId) {
        return templateRepository.findByUserIdOrderByNameAsc(userId);
    }

    @Transactional(readOnly = true)
    public GymTemplate getOwnedTemplate(UUID userId, UUID templateId) {
        return templateRepository.findById(templateId)
                .filter(t -> t.getUserId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Template", templateId));
    }

    @Transactional
    public GymTemplate createTemplate(UUID userId, TemplateRequest request) {
        String name = request.name().trim();
        if (templateRepository.existsByUserIdAndNameIgnoreCase(userId, name)) {
            throw new IllegalArgumentException("Un programme nommé « " + name + " » existe déjà");
        }
        GymTemplate template = templateRepository.save(
                new GymTemplate(userId, name, trimmed(request.goal())));
        variantRepository.save(new GymTemplateVariant(template, "A", null));
        return template;
    }

    @Transactional
    public GymTemplate updateTemplate(UUID userId, UUID templateId, TemplateRequest request) {
        GymTemplate template = getOwnedTemplate(userId, templateId);
        String name = request.name().trim();
        if (!template.getName().equalsIgnoreCase(name)
                && templateRepository.existsByUserIdAndNameIgnoreCase(userId, name)) {
            throw new IllegalArgumentException("Un programme nommé « " + name + " » existe déjà");
        }
        template.update(name, trimmed(request.goal()),
                request.archived() != null ? request.archived() : template.isArchived());
        return template;
    }

    @Transactional
    public void deleteTemplate(UUID userId, UUID templateId) {
        GymTemplate template = getOwnedTemplate(userId, templateId);
        templateRepository.delete(template); // variants and content cascade at the DB level
    }

    @Transactional(readOnly = true)
    public List<GymTemplateVariant> getVariants(UUID userId, UUID templateId) {
        getOwnedTemplate(userId, templateId);
        return variantRepository.findByTemplateIdOrderByLabelAsc(templateId);
    }

    /* ── Variants ─────────────────────────────────────────────────────── */

    @Transactional(readOnly = true)
    public GymTemplateVariant getOwnedVariant(UUID userId, UUID variantId) {
        return variantRepository.findById(variantId)
                .filter(v -> v.getTemplate().getUserId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Variant", variantId));
    }

    @Transactional
    public GymTemplateVariant addVariant(UUID userId, UUID templateId, VariantRequest request) {
        GymTemplate template = getOwnedTemplate(userId, templateId);
        String label = request.label().trim();
        if (variantRepository.existsByTemplateIdAndLabelIgnoreCase(templateId, label)) {
            throw new IllegalArgumentException("La variante « " + label + " » existe déjà");
        }
        return variantRepository.save(new GymTemplateVariant(template, label, trimmed(request.note())));
    }

    /** Duplicate a variant with all its exercises and alternatives — how B is born from A. */
    @Transactional
    public GymTemplateVariant copyVariant(UUID userId, UUID variantId, VariantRequest request) {
        GymTemplateVariant source = getOwnedVariant(userId, variantId);
        GymTemplateVariant copy = addVariant(userId, source.getTemplate().getId(), request);
        for (TemplateExercise te : templateExerciseRepository.findByVariantIdOrderByPositionAsc(variantId)) {
            TemplateExercise cloned = templateExerciseRepository.save(new TemplateExercise(copy,
                    te.getExercise(), te.getPosition(), te.getSets(), te.getReps(), te.getSeconds(),
                    te.getRestSec(), te.getIntensityPct(), te.getNote()));
            for (TemplateExerciseAlternative alt
                    : alternativeRepository.findByTemplateExerciseIdOrderByPositionAsc(te.getId())) {
                alternativeRepository.save(new TemplateExerciseAlternative(cloned,
                        alt.getExercise(), alt.getPosition()));
            }
        }
        return copy;
    }

    @Transactional
    public GymTemplateVariant updateVariant(UUID userId, UUID variantId, VariantRequest request) {
        GymTemplateVariant variant = getOwnedVariant(userId, variantId);
        String label = request.label().trim();
        if (!variant.getLabel().equalsIgnoreCase(label)
                && variantRepository.existsByTemplateIdAndLabelIgnoreCase(
                        variant.getTemplate().getId(), label)) {
            throw new IllegalArgumentException("La variante « " + label + " » existe déjà");
        }
        variant.update(label, trimmed(request.note()));
        return variant;
    }

    @Transactional
    public void deleteVariant(UUID userId, UUID variantId) {
        GymTemplateVariant variant = getOwnedVariant(userId, variantId);
        if (variantRepository.countByTemplateId(variant.getTemplate().getId()) <= 1) {
            throw new IllegalArgumentException(
                    "Un programme garde au moins une variante — supprime le programme entier");
        }
        variantRepository.delete(variant);
    }

    /* ── Prescriptions ────────────────────────────────────────────────── */

    @Transactional(readOnly = true)
    public List<TemplateExercise> getExercises(UUID userId, UUID variantId) {
        getOwnedVariant(userId, variantId);
        return templateExerciseRepository.findByVariantIdOrderByPositionAsc(variantId);
    }

    @Transactional(readOnly = true)
    public List<TemplateExerciseAlternative> getAlternatives(UUID templateExerciseId) {
        return alternativeRepository.findByTemplateExerciseIdOrderByPositionAsc(templateExerciseId);
    }

    /**
     * The full variant read model, assembled INSIDE the transaction: with
     * OSIV off, lazy exercise proxies can't be touched once the service
     * returns, so the DTO mapping has to happen here.
     */
    @Transactional(readOnly = true)
    public VariantDetailResponse getVariantDetail(UUID userId, UUID variantId) {
        GymTemplateVariant variant = getOwnedVariant(userId, variantId);
        List<TemplateExerciseResponse> exercises = templateExerciseRepository
                .findByVariantIdOrderByPositionAsc(variantId).stream()
                .map(te -> TemplateExerciseResponse.from(te,
                        alternativeRepository.findByTemplateExerciseIdOrderByPositionAsc(te.getId())
                                .stream().map(AlternativeResponse::from).toList()))
                .toList();
        return VariantDetailResponse.from(variant, exercises);
    }

    @Transactional
    public TemplateExercise addExercise(UUID userId, UUID variantId, TemplateExerciseRequest request) {
        GymTemplateVariant variant = getOwnedVariant(userId, variantId);
        Exercise exercise = exerciseService.getOwned(userId, request.exerciseId());
        validateEffort(exercise, request);
        int position = (int) templateExerciseRepository.countByVariantId(variantId);
        return templateExerciseRepository.save(new TemplateExercise(variant, exercise, position,
                request.sets(), request.reps(), request.seconds(), request.restSec(),
                request.intensityPct(), trimmed(request.note())));
    }

    @Transactional
    public TemplateExercise updateExercise(UUID userId, UUID templateExerciseId,
                                           TemplateExerciseRequest request) {
        TemplateExercise te = getOwnedTemplateExercise(userId, templateExerciseId);
        Exercise exercise = exerciseService.getOwned(userId, request.exerciseId());
        validateEffort(exercise, request);
        te.swapExercise(exercise);
        te.updatePrescription(request.sets(), request.reps(), request.seconds(),
                request.restSec(), request.intensityPct(), trimmed(request.note()));
        return te;
    }

    @Transactional
    public void removeExercise(UUID userId, UUID templateExerciseId) {
        templateExerciseRepository.delete(getOwnedTemplateExercise(userId, templateExerciseId));
    }

    @Transactional
    public List<TemplateExercise> reorderExercises(UUID userId, UUID variantId, ReorderRequest request) {
        getOwnedVariant(userId, variantId);
        List<TemplateExercise> exercises =
                templateExerciseRepository.findByVariantIdOrderByPositionAsc(variantId);
        if (exercises.size() != request.orderedIds().size()
                || !exercises.stream().map(TemplateExercise::getId).collect(java.util.stream.Collectors.toSet())
                        .equals(java.util.Set.copyOf(request.orderedIds()))) {
            throw new IllegalArgumentException("The new order must contain exactly the variant's exercises");
        }
        for (TemplateExercise te : exercises) {
            te.moveTo(request.orderedIds().indexOf(te.getId()));
        }
        return templateExerciseRepository.findByVariantIdOrderByPositionAsc(variantId);
    }

    /* ── Alternatives ─────────────────────────────────────────────────── */

    @Transactional
    public TemplateExerciseAlternative addAlternative(UUID userId, UUID templateExerciseId,
                                                      UUID exerciseId) {
        TemplateExercise te = getOwnedTemplateExercise(userId, templateExerciseId);
        Exercise exercise = exerciseService.getOwned(userId, exerciseId);
        if (te.getExercise().getId().equals(exerciseId)) {
            throw new IllegalArgumentException("L'alternative ne peut pas être l'exercice lui-même");
        }
        if (alternativeRepository.existsByTemplateExerciseIdAndExerciseId(templateExerciseId, exerciseId)) {
            throw new IllegalArgumentException("Cette alternative est déjà proposée");
        }
        int position = alternativeRepository
                .findByTemplateExerciseIdOrderByPositionAsc(templateExerciseId).size();
        return alternativeRepository.save(new TemplateExerciseAlternative(te, exercise, position));
    }

    @Transactional
    public void removeAlternative(UUID userId, UUID alternativeId) {
        TemplateExerciseAlternative alternative = alternativeRepository.findById(alternativeId)
                .filter(a -> a.getTemplateExercise().getVariant().getTemplate().getUserId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Alternative", alternativeId));
        alternativeRepository.delete(alternative);
    }

    /* ── Internals ────────────────────────────────────────────────────── */

    private TemplateExercise getOwnedTemplateExercise(UUID userId, UUID templateExerciseId) {
        return templateExerciseRepository.findById(templateExerciseId)
                .filter(te -> te.getVariant().getTemplate().getUserId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("TemplateExercise", templateExerciseId));
    }

    /** The prescription must speak the exercise's language: reps OR seconds. */
    private static void validateEffort(Exercise exercise, TemplateExerciseRequest request) {
        if (exercise.getMeasure() == ExerciseMeasure.SECONDS) {
            if (request.seconds() == null) {
                throw new IllegalArgumentException(
                        "« " + exercise.getName() + " » se mesure en secondes — indique une durée");
            }
        } else if (request.reps() == null) {
            throw new IllegalArgumentException(
                    "« " + exercise.getName() + " » se mesure en répétitions — indique un nombre de reps");
        }
    }

    private static String trimmed(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
