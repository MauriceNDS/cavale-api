package com.cavale.training.web;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.cavale.training.course.CourseService;
import com.cavale.training.dto.CourseImportRequest;
import com.cavale.training.dto.CourseResponse;
import com.cavale.training.dto.WaypointRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/objectives/{objectiveId}/course")
@Tag(name = "Course", description = "A race course from GPX, with grade-adjusted pacing")
public class CourseController {

    private final CourseService courseService;

    public CourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    @GetMapping
    @Operation(summary = "The objective's course with its profile, splits and aid-station arrivals")
    public CourseResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID objectiveId) {
        return courseService.getCourse(userId(jwt), objectiveId, LocalDate.now());
    }

    @PostMapping
    @Operation(summary = "Import (or replace) the objective's course from a GPX file")
    public CourseResponse importGpx(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID objectiveId,
                                    @Valid @RequestBody CourseImportRequest request) {
        return courseService.importGpx(userId(jwt), objectiveId, request.gpx(), request.name(),
                LocalDate.now());
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove the objective's course")
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID objectiveId) {
        courseService.delete(userId(jwt), objectiveId);
    }

    @PostMapping("/waypoints")
    @Operation(summary = "Add an aid station / point of interest along the course")
    public CourseResponse addWaypoint(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID objectiveId,
                                      @Valid @RequestBody WaypointRequest request) {
        return courseService.addWaypoint(userId(jwt), objectiveId, request, LocalDate.now());
    }

    @DeleteMapping("/waypoints/{waypointId}")
    @Operation(summary = "Remove a waypoint")
    public CourseResponse deleteWaypoint(@AuthenticationPrincipal Jwt jwt,
                                         @PathVariable UUID objectiveId,
                                         @PathVariable UUID waypointId) {
        return courseService.deleteWaypoint(userId(jwt), waypointId, LocalDate.now());
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
