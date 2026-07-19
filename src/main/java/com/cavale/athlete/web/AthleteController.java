package com.cavale.athlete.web;

import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cavale.athlete.dto.ActivityDetailResponse;
import com.cavale.athlete.dto.ActivityFeedResponse;
import com.cavale.athlete.dto.AthleteContextResponse;
import com.cavale.athlete.dto.AthleteHubResponse;
import com.cavale.athlete.service.ActivityFeedService;
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
    private final ActivityFeedService feedService;
    private final com.cavale.athlete.service.RunningStatsService runningStatsService;

    public AthleteController(AthleteStatsService statsService, AthleteContextService contextService,
                             ActivityFeedService feedService,
                             com.cavale.athlete.service.RunningStatsService runningStatsService) {
        this.statsService = statsService;
        this.contextService = contextService;
        this.feedService = feedService;
        this.runningStatsService = runningStatsService;
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

    @GetMapping("/running-stats")
    @Operation(summary = "Deep running statistics: training load (Banister), weekly effort "
            + "with target band, ACWR, trail volume, efficiency, duration checkpoints, predictions")
    public com.cavale.athlete.dto.RunningStatsResponse runningStats(@AuthenticationPrincipal Jwt jwt) {
        return runningStatsService.getStats(UUID.fromString(jwt.getSubject()));
    }

    @GetMapping("/activities")
    @Operation(summary = "The unified history feed: runs and gym workouts, newest first — "
            + "searchable by name and date range, paginated")
    public ActivityFeedResponse activities(
            @AuthenticationPrincipal Jwt jwt,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "ALL") String type,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String q,
            @org.springframework.web.bind.annotation.RequestParam(required = false)
            java.time.LocalDate from,
            @org.springframework.web.bind.annotation.RequestParam(required = false)
            java.time.LocalDate to,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "20") int size) {
        ActivityFeedResponse.FeedType feedType = "RUN".equalsIgnoreCase(type)
                ? ActivityFeedResponse.FeedType.RUN
                : "GYM".equalsIgnoreCase(type) ? ActivityFeedResponse.FeedType.GYM : null;
        return feedService.feed(UUID.fromString(jwt.getSubject()), feedType, q, from, to,
                Math.max(0, page), Math.min(Math.max(1, size), 50));
    }

    @GetMapping("/activities/{activityId}")
    @Operation(summary = "Full detail of one past activity (opens from the activities page)")
    public ActivityDetailResponse activity(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID activityId) {
        return feedService.activityDetail(UUID.fromString(jwt.getSubject()), activityId);
    }

    @GetMapping("/activities/{activityId}/streams")
    @Operation(summary = "Downsampled Strava streams of the activity (for the detail charts); 404 if none")
    public ResponseEntity<String> activityStreams(@AuthenticationPrincipal Jwt jwt,
                                                  @PathVariable UUID activityId) {
        return feedService.activityStreams(UUID.fromString(jwt.getSubject()), activityId)
                .map(json -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(json))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
