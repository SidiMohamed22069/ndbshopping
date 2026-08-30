package com.ndbshopping.backend.dto.category;

import com.ndbshopping.backend.entity.enums.AttributeValueType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AttributeDefinitionRequest(
        @NotBlank @Size(max = 120) String nomAttribut,
        @NotNull AttributeValueType typeValeur
) {
}
