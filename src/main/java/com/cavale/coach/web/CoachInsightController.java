package com.cavale.coach.web;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cavale.coach.dto.WeeklyInsightResponse;
import com.cavale.coach.service.CoachInsightService;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/coach")
@Tag(name = "Coach", description = "Weekly coach insights and their proposals")
public class CoachInsightController {

    private final CoachInsightService insightService;
    private final ObjectMapper mapper = new ObjectMapper();

    public CoachInsightController(CoachInsightService insightService) {
        this.insightService = insightService;
    }

    @GetMapping("/insights")
    @Operation(summary = "Weekly insights, most recent first")
    public List<WeeklyInsightResponse> list(@AuthenticationPrincipal Jwt jwt,
                                            @RequestParam(defaultValue = "12") int limit) {
        return insightService.list(userId(jwt), Math.clamp(limit, 1, 52)).stream()
                .map(i -> WeeklyInsightResponse.from(i, mapper)).toList();
    }

    @PostMapping("/proposals/{proposalId}/apply")
    @Operation(summary = "Apply a coach proposal to the plan")
    public WeeklyInsightResponse apply(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID proposalId) {
        return WeeklyInsightResponse.from(insightService.applyProposal(userId(jwt), proposalId), mapper);
    }

    @PostMapping("/proposals/{proposalId}/dismiss")
    @Operation(summary = "Dismiss a coach proposal")
    public WeeklyInsightResponse dismiss(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID proposalId) {
        return WeeklyInsightResponse.from(insightService.dismissProposal(userId(jwt), proposalId), mapper);
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
