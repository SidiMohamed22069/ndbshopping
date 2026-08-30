package com.ndbshopping.backend.dto.admin;

import jakarta.validation.constraints.NotNull;

public record UpdateUserStatusRequest(
        @NotNull(message = "Le statut actif est obligatoire")
        Boolean actif
) {
}
