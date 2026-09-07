package com.ndbshopping.backend.dto.order;

import com.ndbshopping.backend.entity.OrderItem;

import java.math.BigDecimal;

/**
 * Ligne de commande, vue admin : identique à {@link OrderItemResponse} mais
 * expose en plus le lien fournisseur externe du produit (Alibaba, AliExpress,
 * Amazon...) pour que l'admin sache où recommander l'article. Réservé aux
 * endpoints {@code /api/admin/**} — {@code sourceUrl} ne doit jamais atteindre
 * le client.
 */
public record AdminOrderItemResponse(
        Long productId,
        String productNom,
        Integer quantite,
        BigDecimal prixUnitaire,
        BigDecimal sousTotal,
        BigDecimal prixActuel,
        String sourceUrl,
        boolean externalSourced
) {
    public static AdminOrderItemResponse from(OrderItem item) {
        BigDecimal sousTotal = item.getPrixUnitaire().multiply(BigDecimal.valueOf(item.getQuantite()));
        return new AdminOrderItemResponse(
                item.getProduct().getId(),
                item.getProduct().getNom(),
                item.getQuantite(),
                item.getPrixUnitaire(),
                sousTotal,
                item.getProduct().getPrix(),
                item.getProduct().getSourceUrl(),
                item.getProduct().isExternalSourced()
        );
    }
}
