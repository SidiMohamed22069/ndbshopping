package com.ndbshopping.backend.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PasswordResetRequestRequest(
        @NotBlank(message = "Le numéro de téléphone est obligatoire")
        @Pattern(regexp = "^[234]\\d{7}$", message = "Numéro mauritanien invalide (8 chiffres commençant par 2, 3 ou 4)")
        String telephone
) {
}
