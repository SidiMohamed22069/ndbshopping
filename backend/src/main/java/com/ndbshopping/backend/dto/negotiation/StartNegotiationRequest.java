package com.ndbshopping.backend.dto.negotiation;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record StartNegotiationRequest(
        @NotNull Long productId,
        @NotNull @DecimalMin(value = "0.01") BigDecimal proposedPrice,
        String message
) {
}
