package com.ndbshopping.backend.dto.order;

import com.ndbshopping.backend.entity.Order;
import com.ndbshopping.backend.entity.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        Long id,
        Long userId,
        String clientNom,
        String clientTelephone,
        String villeLivraison,
        String adresseDetails,
        OrderStatus statut,
        BigDecimal total,
        Instant createdAt,
        List<OrderItemResponse> items
) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getUser().getId(),
                order.getUser().getNom(),
                order.getUser().getTelephone(),
                order.getVilleLivraison(),
                order.getAdresseDetails(),
                order.getStatut(),
                order.getTotal(),
                order.getCreatedAt(),
                order.getItems() == null ? List.of()
                        : order.getItems().stream().map(OrderItemResponse::from).toList()
        );
    }
}
