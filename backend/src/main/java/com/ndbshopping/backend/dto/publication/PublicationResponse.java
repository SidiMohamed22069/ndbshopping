package com.ndbshopping.backend.dto.publication;

import com.ndbshopping.backend.entity.Publication;
import com.ndbshopping.backend.entity.enums.PublicationStatus;
import com.ndbshopping.backend.service.FileStorageService;

import java.time.Instant;

public record PublicationResponse(
        Long id,
        String titre,
        String contenu,
        String imageUrl,
        Long produitLieId,
        Instant datePublication,
        PublicationStatus statut,
        boolean misEnAvant
) {
    public static PublicationResponse from(Publication publication) {
        return new PublicationResponse(
                publication.getId(),
                publication.getTitre(),
                publication.getContenu(),
                FileStorageService.toPublicUrl(publication.getImageUrl()),
                publication.getProduitLie() == null ? null : publication.getProduitLie().getId(),
                publication.getDatePublication(),
                publication.getStatut(),
                publication.isMisEnAvant()
        );
    }
}
