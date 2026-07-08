package com.cavale.training.service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cavale.common.exception.ResourceNotFoundException;
import com.cavale.training.domain.PlanWeek;
import com.cavale.training.domain.PlannedSession;
import com.cavale.training.domain.TrainingPlan;
import com.cavale.training.dto.CreatePlanRequest;
import com.cavale.training.dto.CreateSessionRequest;
import com.cavale.training.dto.CreateWeekRequest;
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

    public TrainingPlanService(TrainingPlanRepository planRepository,
                               PlanWeekRepository weekRepository,
                               PlannedSessionRepository sessionRepository) {
        this.planRepository = planRepository;
        this.weekRepository = weekRepository;
        this.sessionRepository = sessionRepository;
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

    private PlanWeek getOwnedWeek(UUID userId, UUID weekId) {
        return weekRepository.findById(weekId)
                .filter(week -> week.getPlan().getUserId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Week", weekId));
    }
}
