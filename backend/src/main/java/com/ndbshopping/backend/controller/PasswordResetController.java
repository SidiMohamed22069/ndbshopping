package com.ndbshopping.backend.controller;

import com.ndbshopping.backend.dto.auth.MessageResponse;
import com.ndbshopping.backend.dto.auth.PasswordResetConfirmRequest;
import com.ndbshopping.backend.dto.auth.PasswordResetRequestRequest;
import com.ndbshopping.backend.dto.auth.PasswordResetRequestResponse;
import com.ndbshopping.backend.service.PasswordResetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/password-reset")
@Tag(name = "Authentification — Mot de passe oublié")
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    public PasswordResetController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/request")
    @Operation(summary = "Génère un code de réinitialisation (15 min de validité, affiché in-app, pas de SMS payant)")
    public PasswordResetRequestResponse request(@Valid @RequestBody PasswordResetRequestRequest request) {
        return passwordResetService.requestReset(request.telephone());
    }

    @PostMapping("/confirm")
    @Operation(summary = "Vérifie le code et définit le nouveau mot de passe (haché BCrypt)")
    public MessageResponse confirm(@Valid @RequestBody PasswordResetConfirmRequest request) {
        return passwordResetService.confirmReset(request.telephone(), request.code(), request.newPassword());
    }
}
