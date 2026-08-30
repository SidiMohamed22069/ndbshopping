package com.ndbshopping.backend.dto.product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ImportUrlRequest(
        @NotBlank String url,
        @NotNull Long categoryId
) {
}
