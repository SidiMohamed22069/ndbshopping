package com.ndbshopping.backend.dto.negotiation;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record NegotiationOfferRequest(
        @NotNull @DecimalMin(value = "0.01") BigDecimal price,
        String message
) {
}
