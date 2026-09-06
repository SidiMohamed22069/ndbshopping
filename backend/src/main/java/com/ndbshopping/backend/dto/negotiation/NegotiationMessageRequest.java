package com.ndbshopping.backend.dto.negotiation;

import jakarta.validation.constraints.NotBlank;

public record NegotiationMessageRequest(@NotBlank String message) {
}
