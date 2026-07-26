package com.cavale.training.web;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cavale.training.dto.PaceContextResponse;
import com.cavale.training.pace.PaceContextService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/pace-model")
@Tag(name = "Pace model", description = "The athlete's derived paces")
public class PaceModelController {

    private final PaceContextService paceContextService;

    public PaceModelController(PaceContextService paceContextService) {
        this.paceContextService = paceContextService;
    }

    @GetMapping
    @Operation(summary = "Current pace per allure, with the season's road/goal-pace context")
    public PaceContextResponse get(@AuthenticationPrincipal Jwt jwt) {
        return paceContextService.contextFor(UUID.fromString(jwt.getSubject()), LocalDate.now());
    }
}
