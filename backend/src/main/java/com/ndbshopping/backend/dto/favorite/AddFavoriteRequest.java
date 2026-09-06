package com.ndbshopping.backend.dto.favorite;

import jakarta.validation.constraints.NotNull;

public record AddFavoriteRequest(@NotNull Long productId) {
}
