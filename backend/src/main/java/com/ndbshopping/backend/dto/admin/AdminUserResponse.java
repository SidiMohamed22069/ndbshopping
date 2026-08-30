package com.ndbshopping.backend.dto.admin;

import com.ndbshopping.backend.entity.User;
import com.ndbshopping.backend.entity.enums.Role;

import java.time.Instant;

public record AdminUserResponse(
        Long id,
        String nom,
        String telephone,
        Role role,
        boolean telephoneVerifie,
        boolean actif,
        Instant createdAt
) {
    public static AdminUserResponse from(User user) {
        return new AdminUserResponse(
                user.getId(),
                user.getNom(),
                user.getTelephone(),
                user.getRole(),
                user.isTelephoneVerifie(),
                user.isActif(),
                user.getCreatedAt()
        );
    }
}
