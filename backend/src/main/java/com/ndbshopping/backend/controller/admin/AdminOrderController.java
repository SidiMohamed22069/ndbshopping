package com.ndbshopping.backend.controller.admin;

import com.ndbshopping.backend.dto.common.PageResponse;
import com.ndbshopping.backend.dto.order.OrderResponse;
import com.ndbshopping.backend.dto.order.UpdateOrderStatusRequest;
import com.ndbshopping.backend.entity.enums.OrderStatus;
import com.ndbshopping.backend.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/orders")
@Tag(name = "Admin — Commandes")
public class AdminOrderController {

    private final OrderService orderService;

    public AdminOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    @Operation(summary = "Liste des commandes, filtrable par statut et ville")
    public PageResponse<OrderResponse> list(
            @RequestParam(required = false) OrderStatus statut,
            @RequestParam(required = false) String ville,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return orderService.adminSearch(statut, ville, pageable);
    }

    @PatchMapping("/{id}/statut")
    @Operation(summary = "Change le statut d'une commande")
    public OrderResponse updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateOrderStatusRequest request
    ) {
        return orderService.updateStatus(id, request.statut());
    }
}
