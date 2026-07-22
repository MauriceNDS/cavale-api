package com.cavale.training.web;

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

import com.cavale.training.dto.ShoeRequest;
import com.cavale.training.dto.ShoeResponse;
import com.cavale.training.dto.ShoeStatsResponse;
import com.cavale.training.service.ShoeService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/shoes")
@Tag(name = "Shoes", description = "The athlete's shoe rotation and mileage")
public class ShoeController {

    private final ShoeService shoeService;

    public ShoeController(ShoeService shoeService) {
        this.shoeService = shoeService;
    }

    @GetMapping
    @Operation(summary = "The athlete's shoes with accrued mileage, active first")
    public List<ShoeResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return shoeService.list(UUID.fromString(jwt.getSubject()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a pair of shoes")
    public ShoeResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ShoeRequest request) {
        return shoeService.create(UUID.fromString(jwt.getSubject()), request);
    }

    @PutMapping("/{shoeId}")
    @Operation(summary = "Edit a pair of shoes (or retire it)")
    public ShoeResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID shoeId,
                               @Valid @RequestBody ShoeRequest request) {
        return shoeService.update(UUID.fromString(jwt.getSubject()), shoeId, request);
    }

    @DeleteMapping("/{shoeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a pair of shoes (its activities keep their history, unlinked)")
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID shoeId) {
        shoeService.delete(UUID.fromString(jwt.getSubject()), shoeId);
    }

    @GetMapping("/{shoeId}/stats")
    @Operation(summary = "One pair's life in numbers: runs, km, D+, pace, last six months")
    public ShoeStatsResponse stats(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID shoeId) {
        return shoeService.stats(UUID.fromString(jwt.getSubject()), shoeId);
    }

    /** The activity's shoe as a sub-resource: PUT assigns, null clears. */
    public record AssignShoeRequest(UUID shoeId) {
    }

    @PutMapping("/of-activity/{activityId}")
    @Operation(summary = "Assign a pair to an activity after the fact (null clears it)")
    public AssignShoeRequest assignToActivity(@AuthenticationPrincipal Jwt jwt,
                                              @PathVariable UUID activityId,
                                              @RequestBody AssignShoeRequest request) {
        return new AssignShoeRequest(shoeService.assignToActivity(
                UUID.fromString(jwt.getSubject()), activityId, request.shoeId()));
    }
}
