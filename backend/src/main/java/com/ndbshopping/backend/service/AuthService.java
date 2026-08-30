package com.ndbshopping.backend.service;

import com.ndbshopping.backend.dto.auth.AuthResponse;
import com.ndbshopping.backend.dto.auth.LoginOutcome;
import com.ndbshopping.backend.dto.auth.MessageResponse;
import com.ndbshopping.backend.dto.auth.RegisterOrLoginRequest;
import com.ndbshopping.backend.dto.auth.RegisterRequest;
import com.ndbshopping.backend.dto.auth.UserResponse;
import com.ndbshopping.backend.dto.auth.VerifyOtpRequest;
import com.ndbshopping.backend.entity.User;
import com.ndbshopping.backend.entity.enums.Role;
import com.ndbshopping.backend.exception.ApiException;
import com.ndbshopping.backend.repository.UserRepository;
import com.ndbshopping.backend.security.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final OtpService otpService;
    private final JwtUtil jwtUtil;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            OtpService otpService,
            JwtUtil jwtUtil
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.otpService = otpService;
        this.jwtUtil = jwtUtil;
    }

    @Transactional(readOnly = true)
    public LoginOutcome login(RegisterOrLoginRequest request) {
        User user = userRepository.findByTelephone(request.telephone())
                .orElseThrow(() -> ApiException.notFound("Aucun compte avec ce numéro, veuillez vous inscrire."));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw ApiException.unauthorized("Mot de passe incorrect");
        }
        if (!user.isActif()) {
            throw ApiException.forbidden("Compte désactivé");
        }
        if (!user.isTelephoneVerifie()) {
            otpService.sendOtp(request.telephone(), request.langOrDefault());
            return LoginOutcome.needsVerification("Code envoyé");
        }
        return LoginOutcome.authenticated(toAuthResponse(user));
    }

    @Transactional
    public MessageResponse register(RegisterRequest request) {
        if (userRepository.existsByTelephone(request.telephone())) {
            throw ApiException.conflict("Ce numéro de téléphone est déjà utilisé, veuillez vous connecter.");
        }
        User user = User.builder()
                .nom(request.nom().trim())
                .telephone(request.telephone())
                .passwordHash(passwordEncoder.encode(request.password()))
                .telephoneVerifie(false)
                .role(Role.CLIENT)
                .build();
        userRepository.save(user);
        otpService.sendOtp(request.telephone(), request.langOrDefault());
        return new MessageResponse("Code envoyé");
    }

    @Transactional
    public AuthResponse verifyOtp(VerifyOtpRequest request) {
        otpService.verifyOrThrow(request.telephone(), request.code());
        User user = userRepository.findByTelephone(request.telephone())
                .orElseThrow(() -> ApiException.notFound("Utilisateur introuvable"));
        user.setTelephoneVerifie(true);
        return toAuthResponse(user);
    }

    private AuthResponse toAuthResponse(User user) {
        String token = jwtUtil.generateToken(user.getId(), user.getTelephone(), user.getRole().name());
        return new AuthResponse(token, UserResponse.from(user));
    }
}
