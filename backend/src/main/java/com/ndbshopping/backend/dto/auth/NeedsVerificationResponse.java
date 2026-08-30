package com.ndbshopping.backend.dto.auth;

public record NeedsVerificationResponse(boolean needsVerification, String message) {
}
