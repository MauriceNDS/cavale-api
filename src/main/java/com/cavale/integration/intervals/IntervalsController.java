package com.cavale.integration.intervals;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/api/intervals")
@Tag(name = "Intervals.icu", description = "Push planned workouts to the athlete's watch via Intervals.icu")
public class IntervalsController {

    private final IntervalsService service;

    public IntervalsController(IntervalsService service) {
        this.service = service;
    }

    public record ConnectRequest(@NotBlank String apiKey) {
    }

    @GetMapping("/status")
    @Operation(summary = "Connection status for the current user")
    public IntervalsService.IntervalsStatus status(@AuthenticationPrincipal Jwt jwt) {
        return service.status(userId(jwt));
    }

    @PostMapping("/connection")
    @Operation(summary = "Save the athlete's Intervals.icu API key (validated live), then push the upcoming window")
    public IntervalsService.IntervalsStatus connect(@AuthenticationPrincipal Jwt jwt,
                                                    @Valid @RequestBody ConnectRequest request) {
        IntervalsService.IntervalsStatus status = service.connect(userId(jwt), request.apiKey());
        service.pushUpcoming(userId(jwt));
        return status;
    }

    @DeleteMapping("/connection")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Disconnect Intervals.icu")
    public void disconnect(@AuthenticationPrincipal Jwt jwt) {
        service.disconnect(userId(jwt));
    }

    @PostMapping("/push")
    @Operation(summary = "Push the upcoming planned runs to the Intervals.icu calendar (and the watch)")
    public IntervalsService.PushResult push(@AuthenticationPrincipal Jwt jwt) {
        return service.pushUpcoming(userId(jwt));
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
