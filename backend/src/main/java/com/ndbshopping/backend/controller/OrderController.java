package com.ndbshopping.backend.controller;

import com.ndbshopping.backend.dto.common.PageResponse;
import com.ndbshopping.backend.dto.order.CreateOrderRequest;
import com.ndbshopping.backend.dto.order.OrderResponse;
import com.ndbshopping.backend.security.CurrentUserService;
import com.ndbshopping.backend.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@Tag(name = "Commandes (client)")
public class OrderController {

    private final OrderService orderService;
    private final CurrentUserService currentUserService;

    public OrderController(OrderService orderService, CurrentUserService currentUserService) {
        this.orderService = orderService;
        this.currentUserService = currentUserService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crée une commande à partir du panier et notifie l'admin (WebSocket)")
    public OrderResponse create(@Valid @RequestBody CreateOrderRequest request) {
        return orderService.create(currentUserService.requireUser(), request);
    }

    @GetMapping("/me")
    @Operation(summary = "Historique des commandes du client connecté")
    public PageResponse<OrderResponse> mine(@PageableDefault(size = 20) Pageable pageable) {
        return orderService.myOrders(currentUserService.requireUser(), pageable);
    }
}
