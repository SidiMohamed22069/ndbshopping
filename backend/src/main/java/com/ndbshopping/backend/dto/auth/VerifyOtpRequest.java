package com.ndbshopping.backend.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerifyOtpRequest(
        @NotBlank
        @Pattern(regexp = "^[234]\\d{7}$", message = "Numéro mauritanien invalide")
        String telephone,

        @NotBlank(message = "Le code OTP est obligatoire")
        String code
) {
}
