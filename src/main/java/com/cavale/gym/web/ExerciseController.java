package com.cavale.gym.web;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.cavale.gym.dto.ExerciseRequest;
import com.cavale.gym.dto.ExerciseResponse;
import com.cavale.gym.service.ExerciseService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/exercises")
@Tag(name = "Exercises", description = "The exercise library — movements and their theory")
public class ExerciseController {

    private final ExerciseService exerciseService;

    public ExerciseController(ExerciseService exerciseService) {
        this.exerciseService = exerciseService;
    }

    @GetMapping
    @Operation(summary = "The whole library, alphabetical (archived included)")
    public List<ExerciseResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return exerciseService.list(userId(jwt)).stream().map(ExerciseResponse::from).toList();
    }

    @GetMapping("/{exerciseId}")
    @Operation(summary = "One exercise with its theory content")
    public ExerciseResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID exerciseId) {
        return ExerciseResponse.from(exerciseService.getOwned(userId(jwt), exerciseId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an exercise (set derivedFromId to derive from an existing one)")
    public ExerciseResponse create(@AuthenticationPrincipal Jwt jwt,
                                   @Valid @RequestBody ExerciseRequest request) {
        return ExerciseResponse.from(exerciseService.create(userId(jwt), request));
    }

    @PutMapping("/{exerciseId}")
    @Operation(summary = "Full replacement of an exercise (archived flag included)")
    public ExerciseResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID exerciseId,
                                   @Valid @RequestBody ExerciseRequest request) {
        return ExerciseResponse.from(exerciseService.update(userId(jwt), exerciseId, request));
    }

    @DeleteMapping("/{exerciseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete an unused exercise (referenced ones must be archived instead)")
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID exerciseId) {
        exerciseService.delete(userId(jwt), exerciseId);
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
