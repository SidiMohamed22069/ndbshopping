package com.ndbshopping.backend.dto.product;

import com.ndbshopping.backend.entity.enums.ProductSource;

import java.math.BigDecimal;
import java.util.List;

/**
 * Résultat de l'extraction (scraping / métadonnées Open Graph + JSON-LD) d'une page
 * produit externe. Rien n'est encore enregistré : l'admin relit, corrige et complète
 * ces champs dans le formulaire d'ajout avant de publier.
 */
public record ProductImportPreviewResponse(
        String nom,
        String description,
        BigDecimal prixOrigine,
        String deviseOrigine,
        List<String> images,
        Long suggestedCategoryId,
        String suggestedCategoryNom,
        ProductSource sourceOrigine,
        String sourceUrl
) {
}
