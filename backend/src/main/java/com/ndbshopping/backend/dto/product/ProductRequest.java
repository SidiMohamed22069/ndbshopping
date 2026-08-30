package com.ndbshopping.backend.dto.product;

import com.ndbshopping.backend.entity.enums.ProductSource;
import com.ndbshopping.backend.entity.enums.ProductStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record ProductRequest(
        @NotBlank @Size(max = 255) String nom,
        String description,
        @NotNull @DecimalMin(value = "0.0", inclusive = true) BigDecimal prix,
        Integer stock,
        @NotNull Long categoryId,
        ProductSource sourceOrigine,
        String sourceUrl,
        ProductStatus statut,
        @Valid List<ProductAttributeInput> attributs
) {
}
