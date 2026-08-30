package com.ndbshopping.backend.dto.product;

import com.ndbshopping.backend.entity.enums.AttributeValueType;

public record ProductAttributeResponse(
        Long attributeDefinitionId,
        String nomAttribut,
        AttributeValueType typeValeur,
        String valeur
) {
}
