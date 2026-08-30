package com.ndbshopping.backend.dto.admin;

import com.ndbshopping.backend.entity.enums.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank(message = "Le nom est obligatoire")
        @Size(max = 120)
        String nom,

        @NotBlank(message = "Le numéro de téléphone est obligatoire")
        @Pattern(regexp = "^[234]\\d{7}$", message = "Numéro mauritanien invalide (8 chiffres commençant par 2, 3 ou 4)")
        String telephone,

        @NotBlank(message = "Le mot de passe est obligatoire")
        @Size(min = 6, max = 72, message = "Le mot de passe doit contenir entre 6 et 72 caractères")
        String password,

        @NotNull(message = "Le rôle est obligatoire")
        Role role
) {
}
