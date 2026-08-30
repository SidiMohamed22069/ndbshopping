package com.ndbshopping.backend.dto.publication;

import com.ndbshopping.backend.entity.enums.PublicationStatus;
import jakarta.validation.constraints.NotBlank;

public record PublicationRequest(
        @NotBlank String titre,
        @NotBlank String contenu,
        String imageUrl,
        Long produitLieId,
        PublicationStatus statut,
        Boolean misEnAvant
) {
}
