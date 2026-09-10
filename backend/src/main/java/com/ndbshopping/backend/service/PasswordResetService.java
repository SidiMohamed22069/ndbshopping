package com.ndbshopping.backend.service;

import com.ndbshopping.backend.dto.auth.MessageResponse;
import com.ndbshopping.backend.dto.auth.PasswordResetRequestResponse;
import com.ndbshopping.backend.entity.PasswordResetToken;
import com.ndbshopping.backend.entity.User;
import com.ndbshopping.backend.exception.ApiException;
import com.ndbshopping.backend.repository.PasswordResetTokenRepository;
import com.ndbshopping.backend.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * "Mot de passe oublié" : flux 100% in-app, sans SMS payant. Le code de
 * réinitialisation (6 chiffres, valable 15 min) est stocké dans
 * {@link PasswordResetToken} et renvoyé directement dans la réponse de
 * {@link #requestReset} pour être affiché à l'utilisateur (voir
 * accounts/views.py côté Django, qui simule ainsi la réception du SMS).
 */
@Service
public class PasswordResetService {

    private static final Duration CODE_TTL = Duration.ofMinutes(15);
    private static final Duration REQUEST_WINDOW = Duration.ofMinutes(15);
    private static final long MAX_REQUESTS_PER_WINDOW = 5;
    private static final int MAX_VERIFY_ATTEMPTS = 5;

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    public PasswordResetService(
            UserRepository userRepository,
            PasswordResetTokenRepository tokenRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public PasswordResetRequestResponse requestReset(String telephone) {
        User user = userRepository.findByTelephone(telephone.trim())
                .orElseThrow(() -> ApiException.notFound("Aucun compte avec ce numéro."));

        long recent = tokenRepository.countByUserIdAndCreatedAtAfter(user.getId(), Instant.now().minus(REQUEST_WINDOW));
        if (recent >= MAX_REQUESTS_PER_WINDOW) {
            throw ApiException.tooManyRequests("Trop de demandes de réinitialisation. Réessayez plus tard.");
        }

        String code = generateCode();
        Instant expiry = Instant.now().plus(CODE_TTL);
        PasswordResetToken token = PasswordResetToken.builder()
                .user(user)
                .code(code)
                .expiryDate(expiry)
                .used(false)
                .attempts(0)
                .build();
        tokenRepository.save(token);

        return new PasswordResetRequestResponse("Code de réinitialisation généré.", code, expiry);
    }

    /**
     * noRollbackFor=ApiException : un code faux/expiré doit quand même persister
     * l'incrément de `attempts` (protection brute-force) au lieu de faire annuler
     * la mutation par le rollback par défaut de Spring sur RuntimeException.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public MessageResponse confirmReset(String telephone, String code, String newPassword) {
        User user = userRepository.findByTelephone(telephone.trim())
                .orElseThrow(() -> ApiException.notFound("Aucun compte avec ce numéro."));

        PasswordResetToken token = tokenRepository.findTopByUserIdAndUsedFalseOrderByIdDesc(user.getId())
                .orElseThrow(() -> ApiException.badRequest("Aucune demande de réinitialisation en cours. Redemandez un code."));

        if (token.getExpiryDate().isBefore(Instant.now())) {
            throw ApiException.badRequest("Code expiré. Redemandez un code.");
        }
        if (token.getAttempts() >= MAX_VERIFY_ATTEMPTS) {
            token.setUsed(true);
            throw ApiException.tooManyRequests("Trop de tentatives. Redemandez un code.");
        }
        if (!Objects.equals(token.getCode(), code.trim())) {
            token.setAttempts(token.getAttempts() + 1);
            throw ApiException.badRequest("Code incorrect.");
        }

        token.setUsed(true);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        return new MessageResponse("Mot de passe réinitialisé avec succès.");
    }

    private String generateCode() {
        return String.format("%06d", secureRandom.nextInt(1_000_000));
    }
}
