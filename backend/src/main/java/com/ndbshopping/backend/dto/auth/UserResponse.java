package com.ndbshopping.backend.dto.auth;

import com.ndbshopping.backend.entity.User;
import com.ndbshopping.backend.entity.enums.Role;

import java.time.Instant;

public record UserResponse(
        Long id,
        String nom,
        String telephone,
        boolean telephoneVerifie,
        Role role,
        Instant createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getNom(),
                user.getTelephone(),
                user.isTelephoneVerifie(),
                user.getRole(),
                user.getCreatedAt()
        );
    }
}
