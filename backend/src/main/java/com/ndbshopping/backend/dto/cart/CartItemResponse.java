package com.ndbshopping.backend.dto.cart;

import com.ndbshopping.backend.dto.product.ProductResponse;
import com.ndbshopping.backend.entity.CartItem;

import java.math.BigDecimal;

public record CartItemResponse(
        Long id,
        ProductResponse product,
        Integer quantite,
        BigDecimal prixUnitaire,
        BigDecimal sousTotal,
        boolean prixNegocie
) {
    public static CartItemResponse from(CartItem item) {
        ProductResponse product = ProductResponse.from(item.getProduct());
        boolean prixNegocie = item.getPrixConvenu() != null;
        BigDecimal prixUnitaire = prixNegocie ? item.getPrixConvenu() : product.prix();
        BigDecimal sousTotal = prixUnitaire.multiply(BigDecimal.valueOf(item.getQuantite()));
        return new CartItemResponse(item.getId(), product, item.getQuantite(), prixUnitaire, sousTotal, prixNegocie);
    }
}
