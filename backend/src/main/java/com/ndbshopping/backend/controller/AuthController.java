package com.ndbshopping.backend.controller;

import com.ndbshopping.backend.dto.auth.AuthResponse;
import com.ndbshopping.backend.dto.auth.LoginOutcome;
import com.ndbshopping.backend.dto.auth.MessageResponse;
import com.ndbshopping.backend.dto.auth.RegisterOrLoginRequest;
import com.ndbshopping.backend.dto.auth.RegisterRequest;
import com.ndbshopping.backend.dto.auth.UserResponse;
import com.ndbshopping.backend.dto.auth.VerifyOtpRequest;
import com.ndbshopping.backend.security.CurrentUserService;
import com.ndbshopping.backend.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentification")
public class AuthController {

    private final AuthService authService;
    private final CurrentUserService currentUserService;

    public AuthController(AuthService authService, CurrentUserService currentUserService) {
        this.authService = authService;
        this.currentUserService = currentUserService;
    }

    @PostMapping("/register-or-login")
    @Operation(summary = "Connexion : JWT immédiat si le compte est vérifié, sinon indication OTP")
    public ResponseEntity<?> login(@Valid @RequestBody RegisterOrLoginRequest request) {
        LoginOutcome outcome = authService.login(request);
        if (outcome.isPendingVerification()) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(outcome.pending());
        }
        return ResponseEntity.ok(outcome.auth());
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Inscription : crée un compte CLIENT et envoie un code OTP")
    public MessageResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/verify-otp")
    @Operation(summary = "Vérifie le code OTP et retourne un JWT")
    public AuthResponse verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        return authService.verifyOtp(request);
    }

    @GetMapping("/me")
    @Operation(summary = "Profil de l'utilisateur connecté")
    public UserResponse me() {
        return UserResponse.from(currentUserService.requireUser());
    }
}
