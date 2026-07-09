package com.cavale.training.web;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cavale.training.domain.Activity;
import com.cavale.training.domain.PlannedSession;
import com.cavale.training.dto.SessionResponse;
import com.cavale.training.service.TrainingPlanService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/calendar")
@Tag(name = "Calendar", description = "Planned sessions by date range")
public class CalendarController {

    private final TrainingPlanService planService;

    public CalendarController(TrainingPlanService planService) {
        this.planService = planService;
    }

    @GetMapping
    @Operation(summary = "My planned sessions (with actual measures when validated) between two dates")
    public List<SessionResponse> calendar(@AuthenticationPrincipal Jwt jwt,
                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        UUID userId = UUID.fromString(jwt.getSubject());
        List<PlannedSession> sessions = planService.getCalendar(userId, from, to);
        Map<UUID, Activity> activities = planService.getActivitiesForSessions(sessions);
        return sessions.stream()
                .map(s -> SessionResponse.from(s, activities.get(s.getId())))
                .toList();
    }
}
