package com.ndbshopping.backend.dto.order;

import com.ndbshopping.backend.entity.OrderItem;

import java.math.BigDecimal;

public record OrderItemResponse(
        Long productId,
        String productNom,
        Integer quantite,
        BigDecimal prixUnitaire,
        BigDecimal sousTotal
) {
    public static OrderItemResponse from(OrderItem item) {
        BigDecimal sousTotal = item.getPrixUnitaire().multiply(BigDecimal.valueOf(item.getQuantite()));
        return new OrderItemResponse(
                item.getProduct().getId(),
                item.getProduct().getNom(),
                item.getQuantite(),
                item.getPrixUnitaire(),
                sousTotal
        );
    }
}
