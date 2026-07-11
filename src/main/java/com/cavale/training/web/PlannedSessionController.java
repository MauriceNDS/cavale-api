package com.cavale.training.web;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cavale.integration.strava.StravaActivityService;
import com.cavale.training.domain.Activity;
import com.cavale.training.domain.PlannedSession;
import com.cavale.training.dto.SessionResponse;
import com.cavale.training.dto.UpdateSessionRequest;
import com.cavale.training.dto.ValidateSessionRequest;
import com.cavale.training.service.TrainingPlanService;
import com.cavale.training.workout.FitWorkoutExporter;
import com.cavale.training.workout.WorkoutJson;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/sessions")
@Tag(name = "Sessions", description = "Planned session updates")
public class PlannedSessionController {

    private final TrainingPlanService planService;
    private final StravaActivityService stravaActivityService;
    private final FitWorkoutExporter fitExporter;

    public PlannedSessionController(TrainingPlanService planService,
                                    StravaActivityService stravaActivityService,
                                    FitWorkoutExporter fitExporter) {
        this.planService = planService;
        this.stravaActivityService = stravaActivityService;
        this.fitExporter = fitExporter;
    }

    public record ImportStravaRequest(long stravaActivityId) {
    }

    @PatchMapping("/{sessionId}")
    @Operation(summary = "Move a session (date/order) and/or update its status")
    public SessionResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID sessionId,
                                  @Valid @RequestBody UpdateSessionRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return SessionResponse.from(planService.updateSession(userId, sessionId, request));
    }

    @PostMapping("/{sessionId}/validate")
    @Operation(summary = "Validate a running session with actual measures (time + distance required)")
    public SessionResponse validate(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID sessionId,
                                    @Valid @RequestBody ValidateSessionRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        Activity activity = planService.validateSession(userId, sessionId, request);
        return SessionResponse.from(activity.getSession(), activity);
    }

    @PostMapping("/{sessionId}/validate-strava")
    @Operation(summary = "Validate a running session by attaching a Strava activity")
    public SessionResponse validateFromStrava(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID sessionId,
                                              @RequestBody ImportStravaRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        Activity activity = stravaActivityService.importToSession(userId, sessionId, request.stravaActivityId());
        return SessionResponse.from(activity.getSession(), activity);
    }

    @GetMapping("/{sessionId}/export.fit")
    @Operation(summary = "Export the session as a Garmin .fit workout file")
    public ResponseEntity<byte[]> exportFit(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID sessionId) {
        UUID userId = UUID.fromString(jwt.getSubject());
        PlannedSession session = planService.getOwnedSession(userId, sessionId);
        byte[] fit = fitExporter.export(session, WorkoutJson.read(session.getWorkoutJson()));
        String filename = "cavale-" + session.getDate() + ".fit";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(fit);
    }

    @PostMapping("/backfill-workouts")
    @Operation(summary = "Recompute the stored workout structure of all RUN sessions from their text")
    public Map<String, Integer> backfillWorkouts(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return Map.of("updated", planService.backfillWorkouts(userId));
    }
}
