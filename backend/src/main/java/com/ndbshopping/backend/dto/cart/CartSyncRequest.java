package com.ndbshopping.backend.dto.cart;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CartSyncRequest(@NotNull @Valid List<CartItemInput> items) {
}
