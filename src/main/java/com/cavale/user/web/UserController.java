package com.cavale.user.web;

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

import java.util.List;

import com.cavale.user.dto.IssuePatRequest;
import com.cavale.user.dto.PersonalTokenResponse;
import com.cavale.user.dto.UpdateCredentialsRequest;
import com.cavale.user.dto.UpdateProfileRequest;
import com.cavale.user.dto.UpdateStatusRequest;
import com.cavale.user.dto.UserResponse;
import com.cavale.user.service.PersonalTokenService;
import com.cavale.user.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/users")
@Tag(name = "Users", description = "User account operations")
public class UserController {

    private final UserService userService;
    private final PersonalTokenService personalTokenService;

    public UserController(UserService userService, PersonalTokenService personalTokenService) {
        this.userService = userService;
        this.personalTokenService = personalTokenService;
    }

    @GetMapping("/me")
    @Operation(summary = "Get the authenticated user's profile")
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return UserResponse.from(userService.getById(userId));
    }

    @PutMapping("/me/profile")
    @Operation(summary = "Replace the athlete profile (name, weight, height, birth date, HR)")
    public UserResponse updateProfile(@AuthenticationPrincipal Jwt jwt,
                                      @Valid @RequestBody UpdateProfileRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return UserResponse.from(userService.updateProfile(userId, request));
    }

    @PutMapping("/me/credentials")
    @Operation(summary = "Claim a Strava-born account: set a real email + password. "
            + "Refused (409) once the account already has real credentials.")
    public UserResponse setCredentials(@AuthenticationPrincipal Jwt jwt,
                                       @Valid @RequestBody UpdateCredentialsRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return UserResponse.from(userService.setCredentials(userId, request));
    }

    @PutMapping("/me/status")
    @Operation(summary = "Set the athlete's availability (injured, sick, recovering…)")
    public UserResponse updateStatus(@AuthenticationPrincipal Jwt jwt,
                                     @Valid @RequestBody UpdateStatusRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return UserResponse.from(userService.updateStatus(userId, request));
    }

    // NOTE: this exact path is string-matched by AccountAccessFilter.isPatIssue
    // (demo-account guard) — do not move it.
    @PostMapping("/me/pat")
    @Operation(summary = "Issue a long-lived personal access token (MCP client credential). "
            + "Shown once — store it in the MCP client configuration.")
    public PersonalTokenService.IssuedPat issuePat(@AuthenticationPrincipal Jwt jwt,
                                                   @Valid @RequestBody IssuePatRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return personalTokenService.issue(userId, request.label());
    }

    @GetMapping("/me/pats")
    @Operation(summary = "List this account's personal access tokens (labels and dates only — "
            + "the tokens themselves are never stored).")
    public List<PersonalTokenResponse> listPats(@AuthenticationPrincipal Jwt jwt) {
        return personalTokenService.list(UUID.fromString(jwt.getSubject()));
    }

    @DeleteMapping("/me/pats/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Revoke one personal access token — the app holding it loses access "
            + "immediately; sessions and other tokens are untouched.")
    public void revokePat(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        personalTokenService.revoke(UUID.fromString(jwt.getSubject()), id);
    }

    @PostMapping("/me/revoke-tokens")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Revoke every token for this account — all sessions and "
            + "personal access tokens, including the current one. Use if a PAT leaks.")
    public void revokeTokens(@AuthenticationPrincipal Jwt jwt) {
        userService.revokeTokens(UUID.fromString(jwt.getSubject()));
    }
}
