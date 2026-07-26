package com.cavale.training.web;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cavale.training.dto.WeekResponse;
import com.cavale.training.service.PlanCoachService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/plans")
@Tag(name = "Plan coach", description = "Deterministic plan generation")
public class PlanCoachController {

    private final PlanCoachService coachService;

    public PlanCoachController(PlanCoachService coachService) {
        this.coachService = coachService;
    }

    @PostMapping("/{planId}/scaffold")
    @Operation(summary = "Scaffold the periodized week skeleton of an empty plan, "
            + "optionally filled with default sessions")
    public List<WeekResponse> scaffold(@AuthenticationPrincipal Jwt jwt,
                                       @PathVariable UUID planId,
                                       @RequestParam(defaultValue = "false") boolean fillSessions) {
        return coachService.scaffold(UUID.fromString(jwt.getSubject()), planId,
                        LocalDate.now(), fillSessions).stream()
                .map(WeekResponse::from).toList();
    }
}
