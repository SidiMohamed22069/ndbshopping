package com.ndbshopping.backend.dto.analytics;

import jakarta.validation.constraints.NotBlank;

public record HeartbeatRequest(
        @NotBlank String visitorKey,
        boolean authenticated
) {
}
