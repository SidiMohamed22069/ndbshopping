package com.ndbshopping.backend.dto.negotiation;

import com.ndbshopping.backend.entity.PriceNegotiation;
import com.ndbshopping.backend.entity.enums.NegotiationActor;
import com.ndbshopping.backend.entity.enums.PriceNegotiationStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record PriceNegotiationResponse(
        Long id,
        Long productId,
        String productNom,
        BigDecimal productPrixActuel,
        Long userId,
        String userNom,
        String userTelephone,
        BigDecimal prixInitial,
        BigDecimal proposedPrice,
        PriceNegotiationStatus statut,
        NegotiationActor derniereActionPar,
        Instant createdAt,
        Instant updatedAt,
        List<NegotiationMessageResponse> messages
) {
    public static PriceNegotiationResponse from(PriceNegotiation entity) {
        return new PriceNegotiationResponse(
                entity.getId(),
                entity.getProduct().getId(),
                entity.getProduct().getNom(),
                entity.getProduct().getPrix(),
                entity.getUser().getId(),
                entity.getUser().getNom(),
                entity.getUser().getTelephone(),
                entity.getPrixInitial(),
                entity.getProposedPrice(),
                entity.getStatut(),
                entity.getDerniereActionPar(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getMessages() == null ? List.of()
                        : entity.getMessages().stream().map(NegotiationMessageResponse::from).toList()
        );
    }

    /** Version allégée sans les messages, pour les listes (évite de charger tout le fil à chaque ligne). */
    public static PriceNegotiationResponse summary(PriceNegotiation entity) {
        return new PriceNegotiationResponse(
                entity.getId(),
                entity.getProduct().getId(),
                entity.getProduct().getNom(),
                entity.getProduct().getPrix(),
                entity.getUser().getId(),
                entity.getUser().getNom(),
                entity.getUser().getTelephone(),
                entity.getPrixInitial(),
                entity.getProposedPrice(),
                entity.getStatut(),
                entity.getDerniereActionPar(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                List.of()
        );
    }
}
