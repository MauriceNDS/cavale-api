package com.cavale.mcp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Set;

import com.cavale.athlete.dto.AthleteContextResponse;
import com.cavale.athlete.service.AthleteContextService;
import com.cavale.gym.domain.Equipment;
import com.cavale.gym.domain.ExerciseCategory;
import com.cavale.gym.domain.ExerciseMeasure;
import com.cavale.gym.domain.Muscle;
import com.cavale.gym.dto.ExerciseRequest;
import com.cavale.gym.dto.ExerciseResponse;
import com.cavale.gym.dto.TemplateDtos.TemplateExerciseRequest;
import com.cavale.gym.dto.TemplateDtos.TemplateExerciseResponse;
import com.cavale.gym.dto.TemplateDtos.TemplateRequest;
import com.cavale.gym.dto.TemplateDtos.TemplateResponse;
import com.cavale.gym.dto.TemplateDtos.VariantDetailResponse;
import com.cavale.gym.dto.TemplateDtos.VariantRequest;
import com.cavale.gym.dto.TemplateDtos.VariantSummary;
import com.cavale.gym.service.ExerciseService;
import com.cavale.gym.service.GymTemplateService;
import com.cavale.training.domain.Discipline;
import com.cavale.training.domain.ObjectiveType;
import com.cavale.training.domain.SessionStatus;
import com.cavale.training.domain.WeekType;
import com.cavale.training.dto.CreateObjectiveRequest;
import com.cavale.training.dto.CreatePlanRequest;
import com.cavale.training.dto.CreateSessionRequest;
import com.cavale.training.dto.CreateWeekRequest;
import com.cavale.training.dto.ObjectiveResponse;
import com.cavale.training.dto.PlanResponse;
import com.cavale.training.dto.SessionResponse;
import com.cavale.training.dto.UpdateObjectiveRequest;
import com.cavale.training.dto.UpdateSessionRequest;
import com.cavale.training.dto.UpdateWeekRequest;
import com.cavale.training.dto.WeekResponse;
import com.cavale.training.service.ObjectiveService;
import com.cavale.training.service.TrainingPlanService;

/**
 * The MCP face of Cavale: every tool is a thin adapter over the SAME
 * services the REST controllers use — MCP is a second front door, never a
 * parallel code path. The athlete is resolved from the bearer JWT of the
 * MCP HTTP call, so every operation is ownership-scoped exactly like REST.
 */
@Component
public class CoachTools {

    private final AthleteContextService contextService;
    private final TrainingPlanService planService;
    private final ObjectiveService objectiveService;
    private final ExerciseService exerciseService;
    private final GymTemplateService gymTemplateService;

    public CoachTools(AthleteContextService contextService, TrainingPlanService planService,
                      ObjectiveService objectiveService, ExerciseService exerciseService,
                      GymTemplateService gymTemplateService) {
        this.contextService = contextService;
        this.planService = planService;
        this.objectiveService = objectiveService;
        this.exerciseService = exerciseService;
        this.gymTemplateService = gymTemplateService;
    }

    /* ── Read ──────────────────────────────────────────────────────────── */

    @Tool(name = "get_athlete_context", description = """
            Where the athlete is RIGHT NOW. ALWAYS call this before creating or \
            modifying a plan. Returns: availability status (never schedule hard \
            sessions when INJURED or SICK), current season position, last 6 weeks \
            of load with pain flags, recent perceived-effort feedback, the last \
            race with days since (less than 28 days ago => start with recovery \
            weeks), upcoming objectives, distance records and race-time estimates. \
            profile.preferredLanguage ('fr'|'en') is the language to write ALL \
            generated content in: plan names, focus, titles, details, notes.""")
    public AthleteContextResponse getAthleteContext() {
        return contextService.getContext(currentUserId());
    }

    @Tool(name = "list_plans", description = "All training plans (seasons) of the athlete, newest first.")
    public List<PlanResponse> listPlans() {
        return planService.listPlans(currentUserId()).stream().map(PlanResponse::from).toList();
    }

    @Tool(name = "get_plan_weeks", description = "The weeks of a plan, in order, with their type, phase and targets.")
    public List<WeekResponse> getPlanWeeks(@ToolParam(description = "Plan UUID") String planId) {
        return planService.getWeeks(currentUserId(), UUID.fromString(planId)).stream()
                .map(WeekResponse::from).toList();
    }

    @Tool(name = "get_week_sessions", description = "The sessions of one week, with their structured workout.")
    public List<SessionResponse> getWeekSessions(@ToolParam(description = "Week UUID") String weekId) {
        return planService.getWeekSessions(currentUserId(), UUID.fromString(weekId)).stream()
                .map(SessionResponse::from).toList();
    }

    @Tool(name = "get_calendar", description = "Every planned session in a date range (any plan).")
    public List<SessionResponse> getCalendar(
            @ToolParam(description = "First day, ISO date (yyyy-MM-dd)") String from,
            @ToolParam(description = "Last day, ISO date (yyyy-MM-dd)") String to) {
        return planService.getCalendar(currentUserId(), LocalDate.parse(from), LocalDate.parse(to)).stream()
                .map(SessionResponse::from).toList();
    }

    @Tool(name = "list_objectives", description = "The objectives of a plan: the MAIN one plus secondary races.")
    public List<ObjectiveResponse> listObjectives(@ToolParam(description = "Plan UUID") String planId) {
        return objectiveService.listForPlan(currentUserId(), UUID.fromString(planId)).stream()
                .map(ObjectiveResponse::from).toList();
    }

    /* ── Write ─────────────────────────────────────────────────────────── */

    @Tool(name = "create_training_plan", description = """
            Create a new season built around one goal. Call get_athlete_context \
            FIRST and respect it (recent race => recovery start; INJURED/SICK => \
            no hard blocks). Also creates the MAIN objective from the goal; refine \
            it afterwards with update_objective. Then add weeks (add_week) and \
            sessions (add_session).""")
    public PlanResponse createTrainingPlan(
            @ToolParam(description = "Season name, e.g. 'Saison SaintéLyon 2027'") String name,
            @ToolParam(description = "The goal, e.g. 'SaintéLyon 80 km'", required = false) String goal,
            @ToolParam(description = "Season start, ISO date") String startDate,
            @ToolParam(description = "Season end (usually race day), ISO date") String endDate) {
        return PlanResponse.from(planService.createPlan(currentUserId(),
                new CreatePlanRequest(name, goal, LocalDate.parse(startDate), LocalDate.parse(endDate))));
    }

    @Tool(name = "add_week", description = """
            Add one week to a plan. weekType: RECOVERY, TRANSITION, BUILD, DELOAD, \
            SHOCK, TAPER or RACE. startDate must be the Monday of that week, inside \
            the plan's range. Targets are optional coach intent, sessions carry \
            the real work.""")
    public WeekResponse addWeek(
            @ToolParam(description = "Plan UUID") String planId,
            @ToolParam(description = "Week number, 1-based, unique in the plan") int weekNumber,
            @ToolParam(description = "Monday of the week, ISO date") String startDate,
            @ToolParam(description = "Week type") WeekType weekType,
            @ToolParam(description = "Training phase label, e.g. 'Base', 'Spécifique'", required = false) String phase,
            @ToolParam(description = "Target volume in km", required = false) Double targetVolumeKm,
            @ToolParam(description = "Target elevation gain in m", required = false) Integer targetElevationM,
            @ToolParam(description = "One-line focus of the week, in the athlete's preferred language", required = false) String focus) {
        return WeekResponse.from(planService.addWeek(currentUserId(), UUID.fromString(planId),
                new CreateWeekRequest(weekNumber, LocalDate.parse(startDate), phase, weekType,
                        targetVolumeKm != null ? BigDecimal.valueOf(targetVolumeKm) : null,
                        targetElevationM, null, focus)));
    }

    @Tool(name = "add_session", description = """
            Add one session to a week. discipline: RUN, GYM, REST or CROSS. For RUN \
            sessions, write `detail` in the athlete's preferred language using the workout notation the parser \
            understands, e.g. '20′ EF + 3×10′ Seuil 60 (récup 3′) + 10′ EF' — the \
            structured workout (and the watch .fit export) is derived from it. \
            zone examples: 'EF', 'Seuil 60', 'Seuil 30', 'VMA', 'SL'. For GYM \
            sessions, ALWAYS pass templateVariantId (see list_gym_templates) so the \
            athlete can start the live workout from the session.""")
    public SessionResponse addSession(
            @ToolParam(description = "Week UUID") String weekId,
            @ToolParam(description = "Session date, ISO date, inside the week") String date,
            @ToolParam(description = "Discipline") Discipline discipline,
            @ToolParam(description = "Short title, athlete's language, e.g. 'EF vallonné 1h'") String title,
            @ToolParam(description = "Workout text in the athlete's language (see notation above)", required = false) String detail,
            @ToolParam(description = "Main intensity zone", required = false) String zone,
            @ToolParam(description = "Planned duration in minutes", required = false) Integer durationMin,
            @ToolParam(description = "Planned elevation gain in m", required = false) Integer elevationM,
            @ToolParam(description = "RPE range low bound (0-10)", required = false) Integer rpeMin,
            @ToolParam(description = "RPE range high bound (0-10)", required = false) Integer rpeMax,
            @ToolParam(description = "GYM only: program variant UUID", required = false) String templateVariantId) {
        return SessionResponse.from(planService.addSession(currentUserId(), UUID.fromString(weekId),
                new CreateSessionRequest(LocalDate.parse(date), 0, discipline, title, detail, zone,
                        durationMin, elevationM, rpeMin, rpeMax, null,
                        templateVariantId != null ? UUID.fromString(templateVariantId) : null)));
    }

    @Tool(name = "update_session", description = """
            Move a session to another date, change its status (PLANNED, DONE, \
            SKIPPED, MOVED) and/or set a coach comment. Only pass the fields to \
            change. Setting DONE without measures is for GYM/REST sessions; the \
            athlete validates runs from the app.""")
    public SessionResponse updateSession(
            @ToolParam(description = "Session UUID") String sessionId,
            @ToolParam(description = "New date, ISO date", required = false) String date,
            @ToolParam(description = "New status", required = false) SessionStatus status,
            @ToolParam(description = "Coach comment, athlete's language", required = false) String comment) {
        return SessionResponse.from(planService.updateSession(currentUserId(), UUID.fromString(sessionId),
                new UpdateSessionRequest(date != null ? LocalDate.parse(date) : null, null,
                        status, comment, null, null)));
    }

    @Tool(name = "update_week_focus", description = "Set the one-line focus of a week (athlete's preferred language).")
    public WeekResponse updateWeekFocus(
            @ToolParam(description = "Week UUID") String weekId,
            @ToolParam(description = "New focus") String focus) {
        return WeekResponse.from(planService.updateWeek(currentUserId(), UUID.fromString(weekId),
                new UpdateWeekRequest(focus)));
    }

    @Tool(name = "update_objective", description = """
            Refine an objective: race profile (distance, D+, location), date, \
            target time, result time once raced, notes. All fields are replaced — \
            read list_objectives first and resend unchanged values.""")
    public ObjectiveResponse updateObjective(
            @ToolParam(description = "Objective UUID") String objectiveId,
            @ToolParam(description = "Objective type") ObjectiveType type,
            @ToolParam(description = "Name") String name,
            @ToolParam(description = "Event date, ISO date", required = false) String date,
            @ToolParam(description = "Race distance in km", required = false) Double distanceKm,
            @ToolParam(description = "Race elevation gain in m", required = false) Integer elevationGainM,
            @ToolParam(description = "Target time in minutes", required = false) Integer targetTimeMin,
            @ToolParam(description = "Actual result in minutes, once raced", required = false) Integer resultTimeMin,
            @ToolParam(description = "Location", required = false) String location,
            @ToolParam(description = "Notes, athlete's language", required = false) String notes) {
        return ObjectiveResponse.from(objectiveService.update(currentUserId(), UUID.fromString(objectiveId),
                new UpdateObjectiveRequest(type, name,
                        date != null ? LocalDate.parse(date) : null,
                        distanceKm != null ? BigDecimal.valueOf(distanceKm) : null,
                        elevationGainM, targetTimeMin, resultTimeMin, location, notes)));
    }

    @Tool(name = "add_secondary_objective", description = """
            Add an intermediate goal to a plan (a prep race the program accounts \
            for). The MAIN objective already exists — one per plan.""")
    public ObjectiveResponse addSecondaryObjective(
            @ToolParam(description = "Plan UUID") String planId,
            @ToolParam(description = "Objective type") ObjectiveType type,
            @ToolParam(description = "Name") String name,
            @ToolParam(description = "Event date, ISO date", required = false) String date,
            @ToolParam(description = "Race distance in km", required = false) Double distanceKm,
            @ToolParam(description = "Race elevation gain in m", required = false) Integer elevationGainM,
            @ToolParam(description = "Target time in minutes", required = false) Integer targetTimeMin,
            @ToolParam(description = "Location", required = false) String location,
            @ToolParam(description = "Notes, athlete's language", required = false) String notes) {
        return ObjectiveResponse.from(objectiveService.addSecondary(currentUserId(), UUID.fromString(planId),
                new CreateObjectiveRequest(type, name,
                        date != null ? LocalDate.parse(date) : null,
                        distanceKm != null ? BigDecimal.valueOf(distanceKm) : null,
                        elevationGainM, targetTimeMin, location, notes)));
    }

    /* ── Gym: library ─────────────────────────────────────────────────── */

    @Tool(name = "list_exercises", description = """
            The athlete's exercise library. ALWAYS call this before creating an \
            exercise: reuse an existing one when the movement already exists, and \
            derive (derivedFromId) instead of creating a near-duplicate when only \
            the execution differs (slow eccentric, explosive, added load…).""")
    public List<ExerciseResponse> listExercises() {
        return exerciseService.list(currentUserId()).stream().map(ExerciseResponse::from).toList();
    }

    @Tool(name = "create_exercise", description = """
            Add an exercise to the library. Check list_exercises FIRST — duplicate \
            names are rejected, and a movement that differs only in execution must \
            DERIVE from its parent via derivedFromId, not duplicate it. Fill the \
            theory: how to perform it (athlete's language), a resource URL when you know a \
            good one, target muscles, and why it matters for trail running.""")
    public ExerciseResponse createExercise(
            @ToolParam(description = "Name, athlete's language, e.g. 'Squat excentrique lent'") String name,
            @ToolParam(description = "Category") ExerciseCategory category,
            @ToolParam(description = "Equipment") Equipment equipment,
            @ToolParam(description = "How a set is measured") ExerciseMeasure measure,
            @ToolParam(description = "How to perform it, athlete's language", required = false) String description,
            @ToolParam(description = "Video or article URL", required = false) String resourceUrl,
            @ToolParam(description = "Why a trail runner needs it, athlete's language", required = false) String runningBenefit,
            @ToolParam(description = "Target muscles", required = false) Set<Muscle> muscles,
            @ToolParam(description = "Parent exercise UUID when deriving", required = false) String derivedFromId) {
        return ExerciseResponse.from(exerciseService.create(currentUserId(),
                new ExerciseRequest(name, category, equipment, measure, description, resourceUrl,
                        runningBenefit, muscles,
                        derivedFromId != null ? UUID.fromString(derivedFromId) : null, null)));
    }

    /* ── Gym: programs ────────────────────────────────────────────────── */

    @Tool(name = "list_gym_templates", description = """
            The strength programs with their variants (A/B/C) and exercise counts. \
            Check before creating — duplicate names are rejected.""")
    public List<TemplateResponse> listGymTemplates() {
        UUID userId = currentUserId();
        return gymTemplateService.listTemplates(userId).stream()
                .map(template -> TemplateResponse.from(template,
                        gymTemplateService.getVariants(userId, template.getId()).stream()
                                .map(v -> VariantSummary.from(v,
                                        gymTemplateService.getExercises(userId, v.getId()).size()))
                                .toList()))
                .toList();
    }

    @Tool(name = "get_template_variant", description =
            "One variant with its ordered prescriptions and their alternatives.")
    public VariantDetailResponse getTemplateVariant(
            @ToolParam(description = "Variant UUID") String variantId) {
        return gymTemplateService.getVariantDetail(currentUserId(), UUID.fromString(variantId));
    }

    @Tool(name = "create_gym_template", description = """
            Create a strength program (born with variant A). Check \
            list_gym_templates first. Then fill it with add_template_exercise, \
            add variants with add_template_variant.""")
    public TemplateResponse createGymTemplate(
            @ToolParam(description = "Name, e.g. 'Force Max'") String name,
            @ToolParam(description = "Goal, athlete's language", required = false) String goal) {
        UUID userId = currentUserId();
        var template = gymTemplateService.createTemplate(userId, new TemplateRequest(name, goal, null));
        return TemplateResponse.from(template,
                gymTemplateService.getVariants(userId, template.getId()).stream()
                        .map(v -> VariantSummary.from(v, 0)).toList());
    }

    @Tool(name = "add_template_variant", description =
            "Add an empty variant (label B, C…) to a program.")
    public VariantSummary addTemplateVariant(
            @ToolParam(description = "Template UUID") String templateId,
            @ToolParam(description = "Label, e.g. 'B'") String label,
            @ToolParam(description = "Note", required = false) String note) {
        return VariantSummary.from(gymTemplateService.addVariant(currentUserId(),
                UUID.fromString(templateId), new VariantRequest(label, note)), 0);
    }

    @Tool(name = "add_template_exercise", description = """
            Append a prescription to a variant: sets × reps (or seconds for \
            SECONDS exercises), optional rest, % of estimated 1RM and note. The \
            exercise must exist in the library (list_exercises / create_exercise).""")
    public TemplateExerciseResponse addTemplateExercise(
            @ToolParam(description = "Variant UUID") String variantId,
            @ToolParam(description = "Exercise UUID") String exerciseId,
            @ToolParam(description = "Number of sets") int sets,
            @ToolParam(description = "Reps per set (rep-based exercises)", required = false) Integer reps,
            @ToolParam(description = "Seconds per set (SECONDS exercises)", required = false) Integer seconds,
            @ToolParam(description = "Rest between sets, seconds", required = false) Integer restSec,
            @ToolParam(description = "% of estimated 1RM", required = false) Integer intensityPct,
            @ToolParam(description = "Note (tempo…), athlete's language", required = false) String note) {
        return TemplateExerciseResponse.from(gymTemplateService.addExercise(currentUserId(),
                UUID.fromString(variantId), new TemplateExerciseRequest(UUID.fromString(exerciseId),
                        sets, reps, seconds, restSec, intensityPct, note)), List.of());
    }

    @Tool(name = "add_exercise_alternative", description = """
            Add a fallback to a prescription (machine busy / no gym). Prefer a \
            BODYWEIGHT exercise as the no-gym option.""")
    public String addExerciseAlternative(
            @ToolParam(description = "Template exercise UUID (from get_template_variant)") String templateExerciseId,
            @ToolParam(description = "Alternative exercise UUID") String exerciseId) {
        gymTemplateService.addAlternative(currentUserId(), UUID.fromString(templateExerciseId),
                UUID.fromString(exerciseId));
        return "ok";
    }

    /** MCP calls carry the same bearer JWT as REST — the athlete comes from it. */
    private static UUID currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwt) {
            return UUID.fromString(jwt.getToken().getSubject());
        }
        throw new IllegalStateException("No authenticated athlete on this MCP call");
    }
}
