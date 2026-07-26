package com.cavale.gym.service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
import com.cavale.gym.dto.TemplateDtos.GroupAssignment;
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
            cloned.assignGroup(te.getGroupKey()); // supersets travel with the copy
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

    /**
     * Rewrite which prescriptions are chained into supersets, in one atomic
     * call — the whole variant every time, so the editor can merge, split
     * and re-letter in a single request. Members of a group must sit next to
     * each other, since a superset is by definition performed in rotation;
     * a key left with a single member is meaningless and is simply dropped.
     * The group with every exercise in it is what used to be a circuit.
     */
    @Transactional
    public List<TemplateExerciseResponse> assignGroups(UUID userId, UUID variantId,
                                                       List<GroupAssignment> assignments) {
        getOwnedVariant(userId, variantId);
        List<TemplateExercise> exercises =
                templateExerciseRepository.findByVariantIdOrderByPositionAsc(variantId);
        Map<UUID, String> wanted = new LinkedHashMap<>();
        for (GroupAssignment assignment : assignments) {
            wanted.put(assignment.templateExerciseId(), trimmed(assignment.groupKey()));
        }
        if (wanted.size() != exercises.size()
                || !exercises.stream().map(TemplateExercise::getId).collect(Collectors.toSet())
                        .equals(wanted.keySet())) {
            throw new IllegalArgumentException(
                    "L'assignation doit couvrir exactement les exercices de la variante");
        }

        List<String> ordered = exercises.stream().map(te -> wanted.get(te.getId())).toList();
        Set<String> closed = new LinkedHashSet<>();
        for (int i = 0; i < ordered.size(); i++) {
            String key = ordered.get(i);
            if (key == null) {
                continue;
            }
            boolean starting = i == 0 || !key.equals(ordered.get(i - 1));
            if (starting && !closed.add(key)) {
                throw new IllegalArgumentException(
                        "Les exercices d'un même groupe (« " + key + " ») doivent se suivre");
            }
        }

        for (int i = 0; i < exercises.size(); i++) {
            String key = ordered.get(i);
            boolean alone = key != null
                    && (i == 0 || !key.equals(ordered.get(i - 1)))
                    && (i == ordered.size() - 1 || !key.equals(ordered.get(i + 1)));
            exercises.get(i).assignGroup(alone ? null : key);
        }
        return exerciseResponses(variantId);
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
        return VariantDetailResponse.from(variant, exerciseResponses(variantId));
    }

    @Transactional
    public TemplateExercise addExercise(UUID userId, UUID variantId, TemplateExerciseRequest request) {
        GymTemplateVariant variant = getOwnedVariant(userId, variantId);
        Exercise exercise = exerciseService.getOwned(userId, request.exerciseId());
        validateEffort(exercise, request.reps(), request.seconds());
        int position = (int) templateExerciseRepository.countByVariantId(variantId);
        TemplateExercise added = templateExerciseRepository.save(new TemplateExercise(variant,
                exercise, position, request.sets(), request.reps(), request.seconds(),
                request.restSec(), request.intensityPct(), trimmed(request.note())));
        // A new prescription lands last, so it can only join the group ending there.
        added.assignGroup(trimmed(request.groupKey()));
        normalizeGroups(variantId);
        return added;
    }

    @Transactional
    public TemplateExercise updateExercise(UUID userId, UUID templateExerciseId,
                                           TemplateExerciseRequest request) {
        TemplateExercise te = getOwnedTemplateExercise(userId, templateExerciseId);
        Exercise exercise = exerciseService.getOwned(userId, request.exerciseId());
        validateEffort(exercise, request.reps(), request.seconds());
        te.swapExercise(exercise);
        te.updatePrescription(request.sets(), request.reps(), request.seconds(),
                request.restSec(), request.intensityPct(), trimmed(request.note()));
        te.assignGroup(trimmed(request.groupKey()));
        normalizeGroups(te.getVariant().getId());
        return te;
    }

    @Transactional
    public void removeExercise(UUID userId, UUID templateExerciseId) {
        templateExerciseRepository.delete(getOwnedTemplateExercise(userId, templateExerciseId));
    }

    /**
     * Returns the DTO read model, not entities: each prescription's exercise
     * is a lazy proxy, so (as in {@link #getVariantDetail}) the mapping has
     * to happen inside this transaction.
     */
    @Transactional
    public List<TemplateExerciseResponse> reorderExercises(UUID userId, UUID variantId,
                                                           ReorderRequest request) {
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
        normalizeGroups(variantId);
        return exerciseResponses(variantId);
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

    /**
     * Keep the groups sane after the list has been shuffled: dragging an
     * exercise out of the middle of a superset splits it rather than
     * failing, and a key left alone stops being a group at all. The first
     * run of a key wins; a later, detached run is released.
     */
    private void normalizeGroups(UUID variantId) {
        List<TemplateExercise> exercises =
                templateExerciseRepository.findByVariantIdOrderByPositionAsc(variantId);
        Set<String> closed = new LinkedHashSet<>();
        String previous = null;
        for (TemplateExercise te : exercises) {
            String key = te.getGroupKey();
            if (key == null) {
                previous = null;
                continue;
            }
            if (!key.equals(previous) && !closed.add(key)) {
                te.assignGroup(null); // this key already had its run earlier
                previous = null;
                continue;
            }
            previous = key;
        }
        for (int i = 0; i < exercises.size(); i++) {
            String key = exercises.get(i).getGroupKey();
            boolean alone = key != null
                    && (i == 0 || !key.equals(exercises.get(i - 1).getGroupKey()))
                    && (i == exercises.size() - 1 || !key.equals(exercises.get(i + 1).getGroupKey()));
            if (alone) {
                exercises.get(i).assignGroup(null);
            }
        }
    }

    /** Ordered prescriptions of a variant as DTOs — call inside a transaction. */
    private List<TemplateExerciseResponse> exerciseResponses(UUID variantId) {
        return templateExerciseRepository.findByVariantIdOrderByPositionAsc(variantId).stream()
                .map(te -> TemplateExerciseResponse.from(te,
                        alternativeRepository.findByTemplateExerciseIdOrderByPositionAsc(te.getId())
                                .stream().map(AlternativeResponse::from).toList()))
                .toList();
    }

    private TemplateExercise getOwnedTemplateExercise(UUID userId, UUID templateExerciseId) {
        return templateExerciseRepository.findById(templateExerciseId)
                .filter(te -> te.getVariant().getTemplate().getUserId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("TemplateExercise", templateExerciseId));
    }

    /** The prescription must speak the exercise's language: reps OR seconds. */
    public static void validateEffort(Exercise exercise, Integer reps, Integer seconds) {
        if (exercise.getMeasure() == ExerciseMeasure.SECONDS) {
            if (seconds == null) {
                throw new IllegalArgumentException(
                        "« " + exercise.getName() + " » se mesure en secondes — indique une durée");
            }
        } else if (reps == null) {
            throw new IllegalArgumentException(
                    "« " + exercise.getName() + " » se mesure en répétitions — indique un nombre de reps");
        }
    }

    private static String trimmed(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
