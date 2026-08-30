package com.ndbshopping.backend.dto.cart;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CartItemInput(
        @NotNull Long productId,
        @NotNull @Min(1) Integer quantite
) {
}
