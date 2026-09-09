package com.ndbshopping.backend.dto.order;

import jakarta.validation.constraints.NotBlank;

public record CreateOrderRequest(
        String villeLivraison,
        @NotBlank String adresseDetails
) {
}
