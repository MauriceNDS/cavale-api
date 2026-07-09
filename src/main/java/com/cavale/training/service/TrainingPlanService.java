package com.cavale.training.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cavale.common.exception.ResourceNotFoundException;
import com.cavale.training.domain.Activity;
import com.cavale.training.domain.ActivitySource;
import com.cavale.training.domain.Discipline;
import com.cavale.training.domain.PlanWeek;
import com.cavale.training.domain.PlannedSession;
import com.cavale.training.domain.SessionStatus;
import com.cavale.training.domain.TrainingPlan;
import com.cavale.training.dto.CreatePlanRequest;
import com.cavale.training.dto.CreateSessionRequest;
import com.cavale.training.dto.CreateWeekRequest;
import com.cavale.training.dto.UpdateSessionRequest;
import com.cavale.training.dto.UpdateWeekRequest;
import com.cavale.training.dto.ValidateSessionRequest;
import com.cavale.training.repository.ActivityRepository;
import com.cavale.training.repository.PlanWeekRepository;
import com.cavale.training.repository.PlannedSessionRepository;
import com.cavale.training.repository.TrainingPlanRepository;

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

    public TrainingPlanService(TrainingPlanRepository planRepository,
                               PlanWeekRepository weekRepository,
                               PlannedSessionRepository sessionRepository,
                               ActivityRepository activityRepository) {
        this.planRepository = planRepository;
        this.weekRepository = weekRepository;
        this.sessionRepository = sessionRepository;
        this.activityRepository = activityRepository;
    }

    @Transactional
    public TrainingPlan createPlan(UUID userId, CreatePlanRequest request) {
        if (request.endDate().isBefore(request.startDate())) {
            throw new IllegalArgumentException("endDate must not be before startDate");
        }
        TrainingPlan plan = new TrainingPlan(userId, request.name().trim(), request.goal(),
                request.startDate(), request.endDate());
        return planRepository.save(plan);
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
        PlanWeek week = new PlanWeek(plan, request.weekNumber(), request.startDate(), request.phase(),
                request.weekType(), request.targetVolumeKm(), request.targetElevationM(),
                request.targetLoadUa(), request.focus());
        return weekRepository.save(week);
    }

    @Transactional
    public PlannedSession addSession(UUID userId, UUID weekId, CreateSessionRequest request) {
        PlanWeek week = getOwnedWeek(userId, weekId);
        PlannedSession session = new PlannedSession(week, userId, request.date(), request.orderInDay(),
                request.discipline(), request.title().trim(), request.detail(), request.zone(),
                request.durationMin(), request.elevationM(), request.rpeMin(), request.rpeMax());
        return sessionRepository.save(session);
    }

    @Transactional(readOnly = true)
    public List<PlannedSession> getWeekSessions(UUID userId, UUID weekId) {
        getOwnedWeek(userId, weekId);
        return sessionRepository.findByWeekIdOrderByDateAscOrderInDayAsc(weekId);
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
            session.moveTo(newDate, newOrder);
        }
        if (request.status() != null) {
            if (request.status() == SessionStatus.PLANNED) {
                // Un-validating removes the manual measures recorded against it
                activityRepository.findBySessionId(session.getId())
                        .filter(a -> a.getSource() == ActivitySource.MANUAL)
                        .ifPresent(activityRepository::delete);
            }
            session.updateStatus(request.status());
        }
        return session;
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

        if (session.getDiscipline() != Discipline.RUN) {
            throw new IllegalArgumentException(
                    "Only running sessions take validation measures; use the status update instead");
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

    @Transactional
    public void deletePlan(UUID userId, UUID planId) {
        TrainingPlan plan = getOwnedPlan(userId, planId);
        planRepository.delete(plan); // weeks and sessions cascade at the DB level
    }

    private PlanWeek getOwnedWeek(UUID userId, UUID weekId) {
        return weekRepository.findById(weekId)
                .filter(week -> week.getPlan().getUserId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Week", weekId));
    }
}
