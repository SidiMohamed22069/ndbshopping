package com.ndbshopping.backend.dto.admin;

import com.ndbshopping.backend.entity.enums.Role;
import jakarta.validation.constraints.NotNull;

public record UpdateUserRoleRequest(
        @NotNull(message = "Le rôle est obligatoire")
        Role role
) {
}
