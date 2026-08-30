package com.ndbshopping.backend.dto.cart;

import com.ndbshopping.backend.dto.product.ProductResponse;
import com.ndbshopping.backend.entity.CartItem;

import java.math.BigDecimal;

public record CartItemResponse(
        Long id,
        ProductResponse product,
        Integer quantite,
        BigDecimal sousTotal
) {
    public static CartItemResponse from(CartItem item) {
        ProductResponse product = ProductResponse.from(item.getProduct());
        BigDecimal sousTotal = product.prix().multiply(BigDecimal.valueOf(item.getQuantite()));
        return new CartItemResponse(item.getId(), product, item.getQuantite(), sousTotal);
    }
}
