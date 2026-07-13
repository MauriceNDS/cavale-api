package com.cavale.user.web;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cavale.user.dto.UpdateProfileRequest;
import com.cavale.user.dto.UpdateStatusRequest;
import com.cavale.user.dto.UserResponse;
import com.cavale.user.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/users")
@Tag(name = "Users", description = "User account operations")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
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

    @PutMapping("/me/status")
    @Operation(summary = "Set the athlete's availability (injured, sick, recovering…)")
    public UserResponse updateStatus(@AuthenticationPrincipal Jwt jwt,
                                     @Valid @RequestBody UpdateStatusRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return UserResponse.from(userService.updateStatus(userId, request));
    }
}
