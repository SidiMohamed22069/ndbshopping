package com.ndbshopping.backend.dto.favorite;

import com.ndbshopping.backend.dto.product.ProductResponse;
import com.ndbshopping.backend.entity.Favorite;

import java.time.Instant;

public record FavoriteResponse(
        Long id,
        ProductResponse product,
        Instant createdAt
) {
    public static FavoriteResponse from(Favorite favorite) {
        return new FavoriteResponse(
                favorite.getId(),
                ProductResponse.from(favorite.getProduct()),
                favorite.getCreatedAt()
        );
    }
}
