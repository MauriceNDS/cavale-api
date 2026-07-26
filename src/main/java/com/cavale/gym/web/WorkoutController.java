package com.cavale.gym.web;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.cavale.gym.dto.WorkoutDtos.AddExtraBlockRequest;
import com.cavale.gym.dto.WorkoutDtos.AdjustSetsRequest;
import com.cavale.gym.dto.WorkoutDtos.FinishWorkoutRequest;
import com.cavale.gym.dto.WorkoutDtos.LogSetRequest;
import com.cavale.gym.dto.WorkoutDtos.RateSetRequest;
import com.cavale.gym.dto.WorkoutDtos.SetLogResponse;
import com.cavale.gym.dto.WorkoutDtos.StartWorkoutRequest;
import com.cavale.gym.dto.WorkoutDtos.SwapBlockRequest;
import com.cavale.gym.dto.WorkoutDtos.WorkoutBlockResponse;
import com.cavale.gym.dto.WorkoutDtos.WorkoutDetailResponse;
import com.cavale.gym.dto.WorkoutDtos.WorkoutLogResponse;
import com.cavale.gym.service.WorkoutService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/workouts")
@Tag(name = "Workouts", description = "Live strength sessions, logged set by set")
public class WorkoutController {

    private final WorkoutService workoutService;

    public WorkoutController(WorkoutService workoutService) {
        this.workoutService = workoutService;
    }

    @PostMapping
    @Operation(summary = "Start a workout (or return the one already in progress)")
    public WorkoutDetailResponse start(@AuthenticationPrincipal Jwt jwt,
                                       @Valid @RequestBody StartWorkoutRequest request) {
        return workoutService.start(userId(jwt), request);
    }

    @GetMapping("/active")
    @Operation(summary = "The workout in progress, if any (204 otherwise)")
    public ResponseEntity<WorkoutDetailResponse> active(@AuthenticationPrincipal Jwt jwt) {
        return workoutService.active(userId(jwt))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/{workoutLogId}")
    @Operation(summary = "One workout with its blocks, prefills and logged sets")
    public WorkoutDetailResponse get(@AuthenticationPrincipal Jwt jwt,
                                     @PathVariable UUID workoutLogId) {
        return workoutService.get(userId(jwt), workoutLogId);
    }

    @PutMapping("/{workoutLogId}/sets")
    @Operation(summary = "Log one set (autosave — upserted by exercise + set number)")
    public SetLogResponse logSet(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID workoutLogId,
                                 @Valid @RequestBody LogSetRequest request) {
        return workoutService.logSet(userId(jwt), workoutLogId, request);
    }

    @PatchMapping("/sets/{setLogId}/rating")
    @Operation(summary = "Rate a logged set — reps left in reserve, answered during the rest that follows")
    public SetLogResponse rateSet(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID setLogId,
                                  @Valid @RequestBody RateSetRequest request) {
        return workoutService.rateSet(userId(jwt), setLogId, request.rir());
    }

    @DeleteMapping("/sets/{setLogId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a logged set")
    public void deleteSet(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID setLogId) {
        workoutService.deleteSet(userId(jwt), setLogId);
    }

    @PutMapping("/{workoutLogId}/blocks/{templateExerciseId}/sets")
    @Operation(summary = "Adjust a block's set count for this workout only (0 allowed)")
    public WorkoutBlockResponse adjustBlockSets(@AuthenticationPrincipal Jwt jwt,
                                                @PathVariable UUID workoutLogId,
                                                @PathVariable UUID templateExerciseId,
                                                @Valid @RequestBody AdjustSetsRequest request) {
        return workoutService.adjustBlockSets(userId(jwt), workoutLogId, templateExerciseId, request);
    }

    @PutMapping("/{workoutLogId}/extra-blocks/{extraBlockId}/sets")
    @Operation(summary = "Adjust a mid-workout addition's set count (0 allowed)")
    public WorkoutBlockResponse adjustExtraBlockSets(@AuthenticationPrincipal Jwt jwt,
                                                     @PathVariable UUID workoutLogId,
                                                     @PathVariable UUID extraBlockId,
                                                     @Valid @RequestBody AdjustSetsRequest request) {
        return workoutService.adjustExtraBlockSets(userId(jwt), workoutLogId, extraBlockId, request);
    }

    @PutMapping("/{workoutLogId}/blocks/{templateExerciseId}/exercise")
    @Operation(summary = "Replace a block's exercise for this workout only "
            + "(any owned exercise — the prescribed one to revert)")
    public WorkoutBlockResponse swapBlock(@AuthenticationPrincipal Jwt jwt,
                                          @PathVariable UUID workoutLogId,
                                          @PathVariable UUID templateExerciseId,
                                          @Valid @RequestBody SwapBlockRequest request) {
        return workoutService.swapBlock(userId(jwt), workoutLogId, templateExerciseId, request);
    }

    @PostMapping("/{workoutLogId}/blocks/{templateExerciseId}/skip")
    @Operation(summary = "Skip a block for this workout only (undoable, the template is untouched)")
    public WorkoutBlockResponse skipBlock(@AuthenticationPrincipal Jwt jwt,
                                          @PathVariable UUID workoutLogId,
                                          @PathVariable UUID templateExerciseId) {
        return workoutService.skipBlock(userId(jwt), workoutLogId, templateExerciseId);
    }

    @PostMapping("/{workoutLogId}/blocks/{templateExerciseId}/restore")
    @Operation(summary = "Un-skip a block")
    public WorkoutBlockResponse restoreBlock(@AuthenticationPrincipal Jwt jwt,
                                             @PathVariable UUID workoutLogId,
                                             @PathVariable UUID templateExerciseId) {
        return workoutService.restoreBlock(userId(jwt), workoutLogId, templateExerciseId);
    }

    @PostMapping("/{workoutLogId}/extra-blocks")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add an exercise to this workout only (the program is untouched)")
    public WorkoutBlockResponse addExtraBlock(@AuthenticationPrincipal Jwt jwt,
                                              @PathVariable UUID workoutLogId,
                                              @Valid @RequestBody AddExtraBlockRequest request) {
        return workoutService.addExtraBlock(userId(jwt), workoutLogId, request);
    }

    @DeleteMapping("/{workoutLogId}/extra-blocks/{extraBlockId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a mid-workout addition — its logged sets go with it")
    public void removeExtraBlock(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID workoutLogId,
                                 @PathVariable UUID extraBlockId) {
        workoutService.removeExtraBlock(userId(jwt), workoutLogId, extraBlockId);
    }

    @PostMapping("/{workoutLogId}/finish")
    @Operation(summary = "Finish the workout — validates the planned session")
    public WorkoutLogResponse finish(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID workoutLogId,
                                     @Valid @RequestBody FinishWorkoutRequest request) {
        return workoutService.finish(userId(jwt), workoutLogId, request);
    }

    @DeleteMapping("/{workoutLogId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Abandon an in-progress workout (finished ones are history)")
    public void abandon(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID workoutLogId) {
        workoutService.abandon(userId(jwt), workoutLogId);
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
