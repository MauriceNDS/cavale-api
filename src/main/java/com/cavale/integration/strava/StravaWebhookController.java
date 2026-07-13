package com.cavale.integration.strava;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Strava's push callback — the only unauthenticated Strava surface.
 * GET is the one-time subscription validation (echo the challenge iff the
 * verify token matches); POST is the event stream, acked immediately and
 * processed async (Strava requires an answer within 2 seconds).
 */
@RestController
@RequestMapping("/api/strava/webhook")
@Tag(name = "Strava", description = "Strava connection and activity sync")
public class StravaWebhookController {

    private final StravaWebhookService webhookService;
    private final StravaProperties properties;

    public StravaWebhookController(StravaWebhookService webhookService, StravaProperties properties) {
        this.webhookService = webhookService;
        this.properties = properties;
    }

    @GetMapping
    @Operation(summary = "Subscription validation (Strava calls this once, on subscribe)")
    public ResponseEntity<Map<String, String>> validate(
            @RequestParam(name = "hub.mode", required = false) String mode,
            @RequestParam(name = "hub.verify_token", required = false) String verifyToken,
            @RequestParam(name = "hub.challenge", required = false) String challenge) {
        if (!properties.webhookConfigured()
                || !"subscribe".equals(mode)
                || !properties.webhookVerifyToken().equals(verifyToken)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(Map.of("hub.challenge", challenge != null ? challenge : ""));
    }

    @PostMapping
    @Operation(summary = "Event delivery (Strava pushes activity create/update/delete)")
    public ResponseEntity<Void> receive(@RequestBody StravaDtos.WebhookEvent event) {
        webhookService.process(event);
        return ResponseEntity.ok().build();
    }
}
