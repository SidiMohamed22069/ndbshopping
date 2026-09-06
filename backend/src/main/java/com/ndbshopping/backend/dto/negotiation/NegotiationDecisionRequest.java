package com.ndbshopping.backend.dto.negotiation;

/** Corps optionnel pour accept/reject : un mot d'explication si besoin. */
public record NegotiationDecisionRequest(String message) {
}
