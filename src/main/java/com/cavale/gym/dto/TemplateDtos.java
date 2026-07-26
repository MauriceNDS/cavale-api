package com.cavale.gym.dto;

import java.util.List;
import java.util.UUID;

import com.cavale.gym.domain.GymTemplate;
import com.cavale.gym.domain.GymTemplateVariant;
import com.cavale.gym.domain.TemplateExercise;
import com.cavale.gym.domain.TemplateExerciseAlternative;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request/response shapes of the gym template feature. */
public final class TemplateDtos {

    private TemplateDtos() {
    }

    /* ── Requests ─────────────────────────────────────────────────────── */

    public record TemplateRequest(
            @NotBlank(message = "Name is required")
            @Size(max = 150, message = "Name must not exceed 150 characters")
            String name,
            String goal,
            Boolean archived) {
    }

    public record VariantRequest(
            @NotBlank(message = "Label is required")
            @Size(max = 10, message = "Label must not exceed 10 characters")
            String label,
            @Size(max = 300, message = "Note must not exceed 300 characters")
            String note) {
    }

    /** One prescription's superset membership — null key means standalone. */
    public record GroupAssignment(
            @NotNull(message = "Prescription is required")
            UUID templateExerciseId,
            @Size(max = 4, message = "A group key is at most 4 characters")
            String groupKey) {
    }

    /** Rewrites every prescription's grouping at once — the whole variant. */
    public record GroupsRequest(@NotNull List<GroupAssignment> assignments) {
    }

    public record TemplateExerciseRequest(
            @NotNull(message = "Exercise is required")
            UUID exerciseId,
            @Min(value = 1, message = "At least one set")
            int sets,
            @Min(value = 1, message = "Reps must be positive")
            Integer reps,
            @Min(value = 1, message = "Seconds must be positive")
            Integer seconds,
            @Min(value = 0, message = "Rest must not be negative")
            Integer restSec,
            @Min(value = 1, message = "Intensity must be a percentage")
            @Max(value = 200, message = "Intensity must be a percentage")
            Integer intensityPct,
            @Size(max = 300, message = "Note must not exceed 300 characters")
            String note,
            /** Superset this prescription belongs to — shared with its neighbours. */
            @Size(max = 4, message = "A group key is at most 4 characters")
            String groupKey) {
    }

    public record ReorderRequest(@NotNull List<UUID> orderedIds) {
    }

    public record AlternativeRequest(@NotNull UUID exerciseId) {
    }

    /* ── Responses ────────────────────────────────────────────────────── */

    public record VariantSummary(UUID id, String label, String note, long exerciseCount) {

        public static VariantSummary from(GymTemplateVariant variant, long exerciseCount) {
            return new VariantSummary(variant.getId(), variant.getLabel(), variant.getNote(),
                    exerciseCount);
        }
    }

    public record TemplateResponse(UUID id, String name, String goal, boolean archived,
                                   List<VariantSummary> variants) {

        public static TemplateResponse from(GymTemplate template, List<VariantSummary> variants) {
            return new TemplateResponse(template.getId(), template.getName(), template.getGoal(),
                    template.isArchived(), variants);
        }
    }

    public record AlternativeResponse(UUID id, ExerciseResponse exercise) {

        public static AlternativeResponse from(TemplateExerciseAlternative alternative) {
            return new AlternativeResponse(alternative.getId(),
                    ExerciseResponse.from(alternative.getExercise()));
        }
    }

    public record TemplateExerciseResponse(UUID id, int position, ExerciseResponse exercise,
                                           int sets, Integer reps, Integer seconds, Integer restSec,
                                           Integer intensityPct, String note, String groupKey,
                                           List<AlternativeResponse> alternatives) {

        public static TemplateExerciseResponse from(TemplateExercise te,
                                                    List<AlternativeResponse> alternatives) {
            return new TemplateExerciseResponse(te.getId(), te.getPosition(),
                    ExerciseResponse.from(te.getExercise()), te.getSets(), te.getReps(),
                    te.getSeconds(), te.getRestSec(), te.getIntensityPct(), te.getNote(),
                    te.getGroupKey(), alternatives);
        }
    }

    public record VariantDetailResponse(UUID id, UUID templateId, String templateName, String label,
                                        String note, List<TemplateExerciseResponse> exercises) {

        public static VariantDetailResponse from(GymTemplateVariant variant,
                                                 List<TemplateExerciseResponse> exercises) {
            return new VariantDetailResponse(variant.getId(), variant.getTemplate().getId(),
                    variant.getTemplate().getName(), variant.getLabel(), variant.getNote(),
                    exercises);
        }
    }
}
