package com.ndbshopping.backend.dto.auth;

public record AuthResponse(String token, UserResponse user) {
}
