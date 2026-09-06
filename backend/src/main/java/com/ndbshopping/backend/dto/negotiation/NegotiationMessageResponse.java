package com.ndbshopping.backend.dto.negotiation;

import com.ndbshopping.backend.entity.PriceNegotiationMessage;
import com.ndbshopping.backend.entity.enums.Role;

import java.math.BigDecimal;
import java.time.Instant;

public record NegotiationMessageResponse(
        Long id,
        Long senderId,
        String senderNom,
        boolean senderIsAdmin,
        String message,
        BigDecimal offeredPrice,
        Instant createdAt
) {
    public static NegotiationMessageResponse from(PriceNegotiationMessage entity) {
        return new NegotiationMessageResponse(
                entity.getId(),
                entity.getSender().getId(),
                entity.getSender().getNom(),
                entity.getSender().getRole() == Role.ADMIN,
                entity.getMessage(),
                entity.getOfferedPrice(),
                entity.getCreatedAt()
        );
    }
}
