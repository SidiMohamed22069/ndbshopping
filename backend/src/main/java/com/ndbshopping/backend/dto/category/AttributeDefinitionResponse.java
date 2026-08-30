package com.ndbshopping.backend.dto.category;

import com.ndbshopping.backend.entity.CategoryAttributeDefinition;
import com.ndbshopping.backend.entity.enums.AttributeValueType;

public record AttributeDefinitionResponse(
        Long id,
        String nomAttribut,
        AttributeValueType typeValeur
) {
    public static AttributeDefinitionResponse from(CategoryAttributeDefinition def) {
        return new AttributeDefinitionResponse(def.getId(), def.getNomAttribut(), def.getTypeValeur());
    }
}
