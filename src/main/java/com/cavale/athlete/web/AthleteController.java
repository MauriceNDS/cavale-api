package com.cavale.athlete.web;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cavale.athlete.dto.AthleteContextResponse;
import com.cavale.athlete.dto.AthleteHubResponse;
import com.cavale.athlete.service.AthleteContextService;
import com.cavale.athlete.service.AthleteStatsService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/athlete")
@Tag(name = "Athlete", description = "The athlete hub: profile, objectives, records, trends")
public class AthleteController {

    private final AthleteStatsService statsService;
    private final AthleteContextService contextService;

    public AthleteController(AthleteStatsService statsService, AthleteContextService contextService) {
        this.statsService = statsService;
        this.contextService = contextService;
    }

    @GetMapping("/hub")
    @Operation(summary = "Everything the home page shows, in one payload")
    public AthleteHubResponse hub(@AuthenticationPrincipal Jwt jwt) {
        return statsService.getHub(UUID.fromString(jwt.getSubject()));
    }

    @GetMapping("/context")
    @Operation(summary = "Where the athlete is right now — availability, season position, "
            + "recent load and feel, last race, upcoming objectives (the coach/MCP context)")
    public AthleteContextResponse context(@AuthenticationPrincipal Jwt jwt) {
        return contextService.getContext(UUID.fromString(jwt.getSubject()));
    }
}
