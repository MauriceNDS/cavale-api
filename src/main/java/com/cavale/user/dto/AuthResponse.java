package com.cavale.user.dto;

public record AuthResponse(String token, UserResponse user) {
}
