package com.cavale.training.pace;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cavale.training.domain.PlannedSession;
import com.cavale.training.repository.PlannedSessionRepository;

/**
 * Expected km per plan week, from the prescribed session times and the
 * athlete's {@link PaceModel}. The stored week target stays the coach's
 * intent; this is what the prescription will actually produce — the two are
 * shown side by side so a drifting plan is visible before the week is run.
 */
@Service
public class WeekEstimateService {

    private final PaceModelService paceModelService;
    private final PlannedSessionRepository sessionRepository;

    public WeekEstimateService(PaceModelService paceModelService,
                               PlannedSessionRepository sessionRepository) {
        this.paceModelService = paceModelService;
        this.sessionRepository = sessionRepository;
    }

    /** Estimated km by week id for one plan; weeks without RUN sessions are absent. */
    @Transactional(readOnly = true)
    public Map<UUID, BigDecimal> estimatesForPlan(UUID userId, UUID planId) {
        List<PlannedSession> sessions = sessionRepository.findByWeekPlanId(planId);
        if (sessions.isEmpty()) {
            return Map.of();
        }
        PaceModel model = paceModelService.modelFor(userId);
        Map<UUID, BigDecimal> byWeek = new HashMap<>();
        for (PlannedSession session : sessions) {
            BigDecimal km = SessionKmEstimator.estimateKm(session, model);
            if (km != null) {
                byWeek.merge(session.getWeek().getId(), km, BigDecimal::add);
            }
        }
        return byWeek;
    }
}
