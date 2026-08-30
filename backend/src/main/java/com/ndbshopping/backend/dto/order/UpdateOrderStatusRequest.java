package com.ndbshopping.backend.dto.order;

import com.ndbshopping.backend.entity.enums.OrderStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateOrderStatusRequest(@NotNull OrderStatus statut) {
}
