package com.ndbshopping.backend.dto.auth;

/**
 * Résultat de POST /api/auth/register-or-login.
 * Soit un JWT (compte déjà vérifié), soit une demande de vérification OTP.
 */
public record LoginOutcome(AuthResponse auth, NeedsVerificationResponse pending) {

    public static LoginOutcome authenticated(AuthResponse auth) {
        return new LoginOutcome(auth, null);
    }

    public static LoginOutcome needsVerification(String message) {
        return new LoginOutcome(null, new NeedsVerificationResponse(true, message));
    }

    public boolean isPendingVerification() {
        return pending != null;
    }
}
