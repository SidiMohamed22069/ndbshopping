package com.ndbshopping.backend.dto.product;

import jakarta.validation.constraints.NotNull;

public record ProductAttributeInput(
        @NotNull Long attributeDefinitionId,
        @NotNull String valeur
) {
}
