package com.cavale.training.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cavale.common.exception.ResourceNotFoundException;
import com.cavale.gym.service.GymTemplateService;
import com.cavale.training.domain.Activity;
import com.cavale.training.domain.ActivitySource;
import com.cavale.training.domain.Discipline;
import com.cavale.training.domain.Objective;
import com.cavale.training.domain.ObjectiveRole;
import com.cavale.training.domain.ObjectiveType;
import com.cavale.training.domain.PerceivedEffort;
import com.cavale.training.domain.PlanStatus;
import com.cavale.training.domain.PlanWeek;
import com.cavale.training.domain.PlannedSession;
import com.cavale.training.domain.SessionStatus;
import com.cavale.training.domain.TrainingPlan;
import com.cavale.training.dto.CreateObjectiveRequest;
import com.cavale.training.dto.CreatePlanRequest;
import com.cavale.training.dto.CreateSessionRequest;
import com.cavale.training.dto.CreateWeekRequest;
import com.cavale.training.dto.UpdateSessionRequest;
import com.cavale.training.dto.UpdateWeekRequest;
import com.cavale.training.dto.ValidateSessionRequest;
import com.cavale.training.repository.ActivityRepository;
import com.cavale.training.repository.ObjectiveRepository;
import com.cavale.training.repository.PlanWeekRepository;
import com.cavale.training.repository.PlannedSessionRepository;
import com.cavale.training.repository.TrainingPlanRepository;
import com.cavale.training.workout.WorkoutJson;
import com.cavale.training.workout.WorkoutParser;
import com.cavale.training.workout.WorkoutStructure;

/**
 * Business rules for training plans. Every read/write is scoped to the
 * calling user; foreign resources surface as 404 (never 403) so the API
 * doesn't leak other users' data.
 */
@Service
public class TrainingPlanService {

    private final TrainingPlanRepository planRepository;
    private final PlanWeekRepository weekRepository;
    private final PlannedSessionRepository sessionRepository;
    private final ActivityRepository activityRepository;
    private final ObjectiveRepository objectiveRepository;
    private final GymTemplateService gymTemplateService;
    private final ShoeService shoeService;
    private final com.cavale.gym.repository.WorkoutLogRepository workoutLogRepository;

    public TrainingPlanService(TrainingPlanRepository planRepository,
                               PlanWeekRepository weekRepository,
                               PlannedSessionRepository sessionRepository,
                               ActivityRepository activityRepository,
                               ObjectiveRepository objectiveRepository,
                               GymTemplateService gymTemplateService,
                               ShoeService shoeService,
                               com.cavale.gym.repository.WorkoutLogRepository workoutLogRepository) {
        this.planRepository = planRepository;
        this.weekRepository = weekRepository;
        this.sessionRepository = sessionRepository;
        this.activityRepository = activityRepository;
        this.objectiveRepository = objectiveRepository;
        this.gymTemplateService = gymTemplateService;
        this.shoeService = shoeService;
        this.workoutLogRepository = workoutLogRepository;
    }

    /**
     * Creates the plan AND its MAIN objective — a season always has one.
     * See {@link #buildMainObjective} for how the objective is derived.
     */
    @Transactional
    public TrainingPlan createPlan(UUID userId, CreatePlanRequest request) {
        if (request.endDate().isBefore(request.startDate())) {
            throw new IllegalArgumentException("endDate must not be before startDate");
        }
        TrainingPlan plan = new TrainingPlan(userId, request.name().trim(),
                request.goal(), request.startDate(), request.endDate());
        plan.updatePreferences(request.runsPerWeek(), request.gymPerWeek(), request.focus());
        if (request.startDate().isAfter(LocalDate.now())) {
            // The next season, planned ahead — it activates when training starts
            plan.updateStatus(PlanStatus.DRAFT);
        }
        plan = planRepository.save(plan);
        objectiveRepository.save(buildMainObjective(plan, request));
        return plan;
    }

    /**
     * The season's MAIN objective: fully specified when the request carries
     * one, otherwise a placeholder race named after the goal (or the plan),
     * dated at the end of the season.
     */
    private static Objective buildMainObjective(TrainingPlan plan, CreatePlanRequest request) {
        CreateObjectiveRequest details = request.objective();
        if (details == null) {
            String objectiveName = request.goal() == null || request.goal().isBlank()
                    ? plan.getName()
                    : request.goal().trim();
            return new Objective(plan, ObjectiveRole.MAIN, ObjectiveType.RACE,
                    objectiveName, plan.getEndDate());
        }
        Objective objective = new Objective(plan, ObjectiveRole.MAIN, details.type(),
                details.name().trim(),
                details.date() != null ? details.date() : plan.getEndDate());
        ObjectiveService.applyRaceDetails(objective, details.kind(), details.intensity(),
                details.distanceKm(), details.elevationGainM(), details.targetTimeMin(),
                details.location(), details.notes());
        return objective;
    }

    @Transactional(readOnly = true)
    public List<TrainingPlan> listPlans(UUID userId) {
        return planRepository.findByUserIdOrderByStartDateDesc(userId);
    }

    @Transactional(readOnly = true)
    public TrainingPlan getOwnedPlan(UUID userId, UUID planId) {
        return planRepository.findById(planId)
                .filter(plan -> plan.getUserId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Plan", planId));
    }

    @Transactional(readOnly = true)
    public List<PlanWeek> getWeeks(UUID userId, UUID planId) {
        getOwnedPlan(userId, planId);
        return weekRepository.findByPlanIdOrderByWeekNumber(planId);
    }

    @Transactional
    public PlanWeek addWeek(UUID userId, UUID planId, CreateWeekRequest request) {
        TrainingPlan plan = getOwnedPlan(userId, planId);
        if (request.startDate().isBefore(plan.getStartDate())
                || request.startDate().isAfter(plan.getEndDate())) {
            throw new IllegalArgumentException(
                    "week start " + request.startDate() + " is outside the plan range");
        }
        PlanWeek week = new PlanWeek(plan, request.weekNumber(), request.startDate(), request.phase(),
                request.weekType(), request.targetVolumeKm(), request.targetElevationM(),
                request.targetLoadUa(), request.focus());
        return weekRepository.save(week);
    }

    /** The plan's week whose 7-day span contains {@code date}, if any. */
    private Optional<PlanWeek> weekContaining(UUID planId, LocalDate date) {
        return weekRepository.findByPlanIdOrderByWeekNumber(planId).stream()
                .filter(w -> !date.isBefore(w.getStartDate())
                        && date.isBefore(w.getStartDate().plusDays(7)))
                .findFirst();
    }

    @Transactional
    public PlannedSession addSession(UUID userId, UUID weekId, CreateSessionRequest request) {
        PlanWeek week = getOwnedWeek(userId, weekId);
        PlannedSession session = new PlannedSession(week, userId, request.date(), request.orderInDay(),
                request.discipline(), request.title().trim(), request.detail(), request.zone(),
                request.durationMin(), request.elevationM(), request.rpeMin(), request.rpeMax());
        if (request.discipline() == Discipline.RUN) {
            // builder-provided structure wins; otherwise derive it from the text/zone
            List<WorkoutStructure.Node> workout = request.workout() != null && !request.workout().isEmpty()
                    ? request.workout()
                    : WorkoutParser.parse(request.detail(), request.zone(), request.durationMin()).nodes();
            session.updateWorkoutJson(WorkoutJson.write(workout));
        }
        if (request.templateVariantId() != null) {
            linkVariant(userId, session, request.templateVariantId());
        }
        return sessionRepository.save(session);
    }

    /** A strength program only makes sense on a GYM session. */
    private void linkVariant(UUID userId, PlannedSession session, UUID templateVariantId) {
        if (session.getDiscipline() != Discipline.GYM) {
            throw new IllegalArgumentException("Seule une séance Renfo peut suivre un programme");
        }
        session.linkTemplateVariant(gymTemplateService.getOwnedVariant(userId, templateVariantId));
    }

    /** Recomputes the canonical workout structure of every RUN session from its text. */
    @Transactional
    public int backfillWorkouts(UUID userId) {
        List<PlannedSession> sessions = sessionRepository.findByUserId(userId);
        int updated = 0;
        for (PlannedSession session : sessions) {
            if (session.getDiscipline() != Discipline.RUN) {
                continue;
            }
            List<WorkoutStructure.Node> nodes = WorkoutParser
                    .parse(session.getDetail(), session.getZone(), session.getDurationMin())
                    .nodes();
            session.updateWorkoutJson(WorkoutJson.write(nodes));
            updated++;
        }
        return updated;
    }

    @Transactional(readOnly = true)
    public List<PlannedSession> getWeekSessions(UUID userId, UUID weekId) {
        getOwnedWeek(userId, weekId);
        return sessionRepository.findByWeekIdOrderByDateAscOrderInDayAsc(weekId);
    }

    /**
     * Sessions in a date range that belong to the SAME plan as {@code weekId}.
     * Lets a caller reason about neighbours within one season without a
     * DRAFT next-season plan's adjacent days bleeding in (which a whole-athlete
     * calendar query would include).
     */
    @Transactional(readOnly = true)
    public List<PlannedSession> getPlanCalendarForWeek(UUID userId, UUID weekId, LocalDate from, LocalDate to) {
        UUID planId = getOwnedWeek(userId, weekId).getPlan().getId();
        return sessionRepository.findByWeekPlanId(planId).stream()
                .filter(s -> !s.getDate().isBefore(from) && !s.getDate().isAfter(to))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PlannedSession> getCalendar(UUID userId, LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("'to' must not be before 'from'");
        }
        return sessionRepository.findByUserIdAndDateBetweenOrderByDateAscOrderInDayAsc(userId, from, to);
    }

    @Transactional
    public PlanWeek updateWeek(UUID userId, UUID weekId, UpdateWeekRequest request) {
        PlanWeek week = getOwnedWeek(userId, weekId);
        if (request.phase() != null || request.weekType() != null || request.targetVolumeKm() != null
                || request.targetElevationM() != null || request.targetLoadUa() != null) {
            String phase = request.phase() != null
                    ? (request.phase().isBlank() ? null : request.phase().trim())
                    : week.getPhase();
            week.updatePlanning(phase,
                    request.weekType() != null ? request.weekType() : week.getWeekType(),
                    request.targetVolumeKm() != null ? request.targetVolumeKm() : week.getTargetVolumeKm(),
                    request.targetElevationM() != null ? request.targetElevationM() : week.getTargetElevationM(),
                    request.targetLoadUa() != null ? request.targetLoadUa() : week.getTargetLoadUa());
        }
        if (request.focus() != null) {
            week.updateFocus(request.focus().isBlank() ? null : request.focus().trim());
        }
        return week;
    }

    @Transactional
    public PlannedSession updateSession(UUID userId, UUID sessionId, UpdateSessionRequest request) {
        PlannedSession session = sessionRepository.findById(sessionId)
                .filter(s -> s.getUserId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Session", sessionId));

        if (request.date() != null || request.orderInDay() != null) {
            LocalDate newDate = request.date() != null ? request.date() : session.getDate();
            TrainingPlan plan = session.getWeek().getPlan();
            if (newDate.isBefore(plan.getStartDate()) || newDate.isAfter(plan.getEndDate())) {
                throw new IllegalArgumentException("date " + newDate + " is outside the plan range");
            }
            int newOrder = request.orderInDay() != null ? request.orderInDay() : session.getOrderInDay();
            boolean moves = !newDate.equals(session.getDate()) || newOrder != session.getOrderInDay();
            if (moves && (session.getStatus() == SessionStatus.DONE
                    || session.getStatus() == SessionStatus.SKIPPED)) {
                throw new IllegalArgumentException(
                        "a " + session.getStatus() + " session cannot be moved");
            }
            session.moveTo(newDate, newOrder);
            // Keep the session in the week its new date falls in, so weekly
            // progress attributes its load to the right week (not the old one).
            weekContaining(plan.getId(), newDate)
                    .filter(w -> !w.getId().equals(session.getWeek().getId()))
                    .ifPresent(session::reassignWeek);
        }
        if (request.title() != null || request.detail() != null || request.zone() != null
                || request.durationMin() != null || request.elevationM() != null
                || request.rpeMin() != null || request.rpeMax() != null) {
            if (request.title() != null && request.title().isBlank()) {
                throw new IllegalArgumentException("title must not be blank");
            }
            String title = request.title() != null ? request.title().trim() : session.getTitle();
            String detail = request.detail() != null
                    ? (request.detail().isBlank() ? null : request.detail().trim())
                    : session.getDetail();
            String zone = request.zone() != null
                    ? (request.zone().isBlank() ? null : request.zone().trim())
                    : session.getZone();
            Integer durationMin = request.durationMin() != null ? request.durationMin() : session.getDurationMin();
            session.updateContent(title, detail, zone, durationMin,
                    request.elevationM() != null ? request.elevationM() : session.getElevationM(),
                    request.rpeMin() != null ? request.rpeMin() : session.getRpeMin(),
                    request.rpeMax() != null ? request.rpeMax() : session.getRpeMax());
            if (session.getDiscipline() == Discipline.RUN && request.workout() == null) {
                // the prescription changed — keep the derived workout in sync
                session.updateWorkoutJson(WorkoutJson.write(
                        WorkoutParser.parse(detail, zone, durationMin).nodes()));
            }
        }
        if (request.status() != null) {
            if (request.status() == SessionStatus.PLANNED) {
                releaseActivity(session);
            }
            session.updateStatus(request.status());
        }
        if (request.comment() != null) {
            session.updateComment(request.comment().isBlank() ? null : request.comment().trim());
        }
        if (request.workout() != null) {
            session.updateWorkoutJson(WorkoutJson.write(request.workout()));
        }
        if (request.templateVariantId() != null) {
            linkVariant(userId, session, request.templateVariantId());
        }
        return session;
    }

    @Transactional(readOnly = true)
    public PlannedSession getOwnedSession(UUID userId, UUID sessionId) {
        return sessionRepository.findById(sessionId)
                .filter(s -> s.getUserId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Session", sessionId));
    }

    /**
     * Validate a running session with actual measures (time + distance required).
     * Creates or replaces the MANUAL activity and marks the session DONE.
     */
    @Transactional
    public Activity validateSession(UUID userId, UUID sessionId, ValidateSessionRequest request) {
        PlannedSession session = sessionRepository.findById(sessionId)
                .filter(s -> s.getUserId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Session", sessionId));

        if (session.getDiscipline() != Discipline.RUN && session.getDiscipline() != Discipline.CROSS) {
            throw new IllegalArgumentException(
                    "Only running and cross-training sessions take validation measures; "
                            + "use the status update instead");
        }

        Activity activity = activityRepository.findBySessionId(session.getId())
                .map(existing -> {
                    existing.updateMeasures(request.durationMin(), request.distanceKm(),
                            request.elevationM(), request.avgHr(), request.comment());
                    return existing;
                })
                .orElseGet(() -> activityRepository.save(new Activity(session, ActivitySource.MANUAL,
                        session.getDate(), request.durationMin(), request.distanceKm(),
                        request.elevationM(), request.avgHr(), request.comment())));
        activity.markDiscipline(session.getDiscipline());
        activity.assignShoe(shoeService.requireOwned(userId, request.shoeId()));
        activity.recordFeedback(
                request.perceivedEffort() != null ? request.perceivedEffort() : PerceivedEffort.COMME_PREVU,
                request.comment(), request.pain());

        session.updateStatus(SessionStatus.DONE);
        return activity;
    }

    @Transactional(readOnly = true)
    public Map<UUID, Activity> getActivitiesForSessions(List<PlannedSession> sessions) {
        if (sessions.isEmpty()) return Map.of();
        List<UUID> ids = sessions.stream().map(PlannedSession::getId).toList();
        return activityRepository.findBySessionIdIn(ids).stream()
                .collect(Collectors.toMap(a -> a.getSession().getId(), a -> a));
    }

    /**
     * Real minutes spent on each GYM session, from its finished workout log —
     * the estimate stays on the session, the truth lives on the log.
     */
    public Map<UUID, Integer> getWorkoutDurationsForSessions(List<PlannedSession> sessions) {
        List<UUID> gymIds = sessions.stream()
                .filter(s -> s.getDiscipline() == Discipline.GYM)
                .map(PlannedSession::getId)
                .toList();
        if (gymIds.isEmpty()) return Map.of();
        return workoutLogRepository.findBySessionIdIn(gymIds).stream()
                .filter(w -> w.getStatus() == com.cavale.gym.domain.WorkoutStatus.FINISHED
                        && w.getDurationMin() != null)
                .collect(Collectors.toMap(w -> w.getSession().getId(),
                        com.cavale.gym.domain.WorkoutLog::getDurationMin));
    }

    @Transactional
    public void deletePlan(UUID userId, UUID planId) {
        TrainingPlan plan = getOwnedPlan(userId, planId);
        planRepository.delete(plan); // weeks and sessions cascade at the DB level
    }

    /** Remove one planned session; its actuals are released, never lost with it. */
    @Transactional
    public void deleteSession(UUID userId, UUID sessionId) {
        PlannedSession session = sessionRepository.findById(sessionId)
                .filter(s -> s.getUserId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Session", sessionId));
        releaseActivity(session);
        sessionRepository.delete(session);
    }

    /** Remove a week and its sessions; each session's actuals are released first. */
    @Transactional
    public void deleteWeek(UUID userId, UUID weekId) {
        PlanWeek week = getOwnedWeek(userId, weekId);
        sessionRepository.findByWeekIdOrderByDateAscOrderInDayAsc(weekId)
                .forEach(this::releaseActivity);
        weekRepository.delete(week); // its sessions cascade at the DB level
    }

    /**
     * Clears a session's actuals: a manually-entered measure is discarded; a
     * real Strava run is detached (returned to the unmatched pool) so it stops
     * counting as this session's volume and can be re-proposed elsewhere.
     */
    private void releaseActivity(PlannedSession session) {
        activityRepository.findBySessionId(session.getId()).ifPresent(activity -> {
            if (activity.getSource() == ActivitySource.MANUAL) {
                activityRepository.delete(activity);
            } else {
                activity.detachFromSession();
            }
        });
    }

    private PlanWeek getOwnedWeek(UUID userId, UUID weekId) {
        return weekRepository.findById(weekId)
                .filter(week -> week.getPlan().getUserId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Week", weekId));
    }
}
