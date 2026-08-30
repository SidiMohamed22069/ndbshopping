package com.ndbshopping.backend.dto.product;

import jakarta.validation.constraints.NotBlank;

public record RejectProductRequest(@NotBlank String raison) {
}
