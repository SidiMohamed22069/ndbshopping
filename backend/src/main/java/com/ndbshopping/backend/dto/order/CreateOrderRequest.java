package com.ndbshopping.backend.dto.order;

import jakarta.validation.constraints.NotBlank;

public record CreateOrderRequest(
        /** Ignoré : la livraison est toujours Nouadhibou. Conservé pour compatibilité des anciens clients. */
        String villeLivraison,
        @NotBlank String adresseDetails
) {
}
