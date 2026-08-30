package com.ndbshopping.backend.dto.category;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ReorderCategoriesRequest(
        @NotEmpty List<@NotNull Long> ordreIds
) {
}
