package com.ndbshopping.backend.dto.order;

import com.ndbshopping.backend.entity.OrderItem;

import java.math.BigDecimal;

public record OrderItemResponse(
        Long productId,
        String productNom,
        Integer quantite,
        BigDecimal prixUnitaire,
        BigDecimal sousTotal,
        BigDecimal prixActuel
) {
    /**
     * prixUnitaire est figé au moment de la commande (historique financier exact).
     * prixActuel est le prix courant du produit — sert uniquement à signaler côté
     * admin un écart depuis la commande, jamais à recalculer le total commandé.
     */
    public static OrderItemResponse from(OrderItem item) {
        BigDecimal sousTotal = item.getPrixUnitaire().multiply(BigDecimal.valueOf(item.getQuantite()));
        return new OrderItemResponse(
                item.getProduct().getId(),
                item.getProduct().getNom(),
                item.getQuantite(),
                item.getPrixUnitaire(),
                sousTotal,
                item.getProduct().getPrix()
        );
    }
}
