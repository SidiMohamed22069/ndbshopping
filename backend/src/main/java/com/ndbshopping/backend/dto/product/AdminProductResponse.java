package com.ndbshopping.backend.dto.product;

import com.ndbshopping.backend.entity.Product;
import com.ndbshopping.backend.entity.enums.ProductEtat;
import com.ndbshopping.backend.entity.enums.ProductSource;
import com.ndbshopping.backend.entity.enums.ProductStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Vue admin d'un produit : identique à {@link ProductResponse} mais expose en
 * plus {@code sourceUrl} (lien fournisseur Alibaba/AliExpress/Amazon...) et
 * {@code externalSourced}. Réservé aux endpoints {@code /api/admin/**} — ne
 * jamais réutiliser côté catalogue public ou "mes annonces".
 */
public record AdminProductResponse(
        Long id,
        String nom,
        String description,
        BigDecimal prix,
        Integer stock,
        Long categoryId,
        String categoryNom,
        ProductSource sourceOrigine,
        String sourceUrl,
        boolean externalSourced,
        ProductStatus statut,
        String ville,
        ProductEtat etat,
        Long soumisParUserId,
        String raisonRejet,
        Instant createdAt,
        Instant updatedAt,
        List<ProductImageResponse> images,
        List<ProductVideoResponse> videos,
        boolean aVideo,
        List<ProductAttributeResponse> attributs
) {
    public static AdminProductResponse from(Product product) {
        List<ProductImageResponse> images = ProductResponse.images(product);
        List<ProductVideoResponse> videos = ProductResponse.videos(product);
        List<ProductAttributeResponse> attributs = ProductResponse.attributs(product);
        return new AdminProductResponse(
                product.getId(),
                product.getNom(),
                product.getDescription(),
                product.getPrix(),
                product.getStock(),
                product.getCategory().getId(),
                product.getCategory().getNom(),
                product.getSourceOrigine(),
                product.getSourceUrl(),
                product.isExternalSourced(),
                product.getStatut(),
                product.getVille(),
                product.getEtat(),
                product.getSoumisPar() == null ? null : product.getSoumisPar().getId(),
                product.getRaisonRejet(),
                product.getCreatedAt(),
                product.getUpdatedAt(),
                images,
                videos,
                !videos.isEmpty(),
                attributs
        );
    }
}
