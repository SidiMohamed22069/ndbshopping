package com.ndbshopping.backend.dto.auth;

import java.time.Instant;

/**
 * {@code code} n'est présent que parce que ce flux n'utilise pas de SMS payant :
 * il est affiché à l'utilisateur sur l'écran de confirmation (simulation
 * in-app), à la place d'un envoi SMS réel — voir PasswordResetToken.
 */
public record PasswordResetRequestResponse(String message, String code, Instant expiresAt) {
}
