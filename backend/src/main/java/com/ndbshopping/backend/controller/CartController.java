package com.ndbshopping.backend.controller;

import com.ndbshopping.backend.dto.cart.CartResponse;
import com.ndbshopping.backend.dto.cart.CartSyncRequest;
import com.ndbshopping.backend.security.CurrentUserService;
import com.ndbshopping.backend.service.CartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cart")
@Tag(name = "Panier")
public class CartController {

    private final CartService cartService;
    private final CurrentUserService currentUserService;

    public CartController(CartService cartService, CurrentUserService currentUserService) {
        this.cartService = cartService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    @Operation(summary = "Panier de l'utilisateur connecté")
    public CartResponse get() {
        return cartService.getCart(currentUserService.requireUser());
    }

    @PostMapping("/sync")
    @Operation(summary = "Fusionne le panier local (invité) avec le panier persisté")
    public CartResponse sync(@Valid @RequestBody CartSyncRequest request) {
        return cartService.sync(currentUserService.requireUser(), request);
    }
}
