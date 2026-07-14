package com.cavale.gym.web;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cavale.gym.dto.GymStatsResponse;
import com.cavale.gym.service.GymStatsService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/gym/stats")
@Tag(name = "Gym stats", description = "Strength progression: 1RM trends, tonnage, balance, PRs")
public class GymStatsController {

    private final GymStatsService statsService;

    public GymStatsController(GymStatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping
    @Operation(summary = "Everything the gym stats page draws, in one payload")
    public GymStatsResponse stats(@AuthenticationPrincipal Jwt jwt) {
        return statsService.getStats(UUID.fromString(jwt.getSubject()));
    }
}
