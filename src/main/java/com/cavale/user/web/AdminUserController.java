package com.cavale.user.web;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cavale.user.domain.AccountStatus;
import com.cavale.user.dto.AdminUserResponse;
import com.cavale.user.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Admin-only account administration. Every {@code /api/admin/**} request is
 * gated to ROLE_ADMIN by {@code AccountAccessFilter}, so the controller stays
 * thin.
 */
@RestController
@RequestMapping("/api/admin/users")
@Tag(name = "Admin", description = "Account administration (admin only)")
public class AdminUserController {

    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @Operation(summary = "List all accounts, optionally filtered by access status "
            + "(PENDING = new, ACTIVE = activated, DISABLED = deactivated)")
    public List<AdminUserResponse> list(@RequestParam(required = false) AccountStatus status) {
        return userService.listUsers(status).stream().map(AdminUserResponse::from).toList();
    }

    @PostMapping("/{id}/activate")
    @Operation(summary = "Grant an account access (PENDING/DISABLED → ACTIVE)")
    public AdminUserResponse activate(@PathVariable UUID id) {
        return AdminUserResponse.from(userService.activate(id));
    }

    @PostMapping("/{id}/deactivate")
    @Operation(summary = "Revoke an account's access (→ DISABLED)")
    public AdminUserResponse deactivate(@PathVariable UUID id) {
        return AdminUserResponse.from(userService.deactivate(id));
    }
}
