package com.ndbshopping.backend.dto.product;

import com.ndbshopping.backend.entity.Product;
import com.ndbshopping.backend.entity.ProductAttributeValue;
import com.ndbshopping.backend.entity.ProductImage;
import com.ndbshopping.backend.entity.ProductVideo;
import com.ndbshopping.backend.entity.enums.ProductEtat;
import com.ndbshopping.backend.entity.enums.ProductSource;
import com.ndbshopping.backend.entity.enums.ProductStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * Vue publique / catalogue d'un produit. Ne contient volontairement PAS
 * {@code sourceUrl} : ce lien fournisseur (Alibaba, AliExpress...) est réservé
 * à l'admin — voir {@link AdminProductResponse}, utilisé uniquement par les
 * endpoints sous {@code /api/admin/**}.
 */
public record ProductResponse(
        Long id,
        String nom,
        String description,
        BigDecimal prix,
        Integer stock,
        Long categoryId,
        String categoryNom,
        ProductSource sourceOrigine,
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
    public static ProductResponse from(Product product) {
        List<ProductImageResponse> images = images(product);
        List<ProductVideoResponse> videos = videos(product);
        List<ProductAttributeResponse> attributs = attributs(product);
        return new ProductResponse(
                product.getId(),
                product.getNom(),
                product.getDescription(),
                product.getPrix(),
                product.getStock(),
                product.getCategory().getId(),
                product.getCategory().getNom(),
                product.getSourceOrigine(),
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

    static List<ProductImageResponse> images(Product product) {
        return product.getImages() == null ? List.of()
                : product.getImages().stream()
                .sorted(Comparator.comparingInt(ProductImage::getOrdre).thenComparing(ProductImage::getId))
                .map(img -> new ProductImageResponse(img.getId(), toMediaUrl(img.getRelativePath()), img.getOrdre()))
                .toList();
    }

    static List<ProductVideoResponse> videos(Product product) {
        return product.getVideos() == null ? List.of()
                : product.getVideos().stream()
                .sorted(Comparator.comparingInt(ProductVideo::getOrdre).thenComparing(ProductVideo::getId))
                .map(vid -> new ProductVideoResponse(
                        vid.getId(),
                        toMediaUrl(vid.getRelativePath()),
                        vid.getRelativePath(),
                        vid.getOrdre()))
                .toList();
    }

    static List<ProductAttributeResponse> attributs(Product product) {
        return product.getAttributes() == null ? List.of()
                : product.getAttributes().stream()
                .map(ProductResponse::toAttr)
                .toList();
    }

    private static ProductAttributeResponse toAttr(ProductAttributeValue value) {
        return new ProductAttributeResponse(
                value.getAttributeDefinition().getId(),
                value.getAttributeDefinition().getNomAttribut(),
                value.getAttributeDefinition().getTypeValeur(),
                value.getValeur()
        );
    }

    static String toMediaUrl(String relativePath) {
        String path = relativePath.replace("\\", "/");
        return path.startsWith("/") ? "/media" + path : "/media/" + path;
    }
}
