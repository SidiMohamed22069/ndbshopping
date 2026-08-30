package com.ndbshopping.backend.dto.category;

import com.ndbshopping.backend.entity.enums.CategoryType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
        @NotBlank @Size(max = 150) String nom,
        @NotNull CategoryType type,
        Long parentId,
        String imageUrl
) {
}
