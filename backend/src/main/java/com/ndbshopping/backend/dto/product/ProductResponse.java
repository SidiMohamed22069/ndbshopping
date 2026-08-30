package com.ndbshopping.backend.dto.product;

import com.ndbshopping.backend.entity.Product;
import com.ndbshopping.backend.entity.ProductAttributeValue;
import com.ndbshopping.backend.entity.ProductImage;
import com.ndbshopping.backend.entity.ProductVideo;
import com.ndbshopping.backend.entity.enums.ProductSource;
import com.ndbshopping.backend.entity.enums.ProductStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

public record ProductResponse(
        Long id,
        String nom,
        String description,
        BigDecimal prix,
        Integer stock,
        Long categoryId,
        String categoryNom,
        ProductSource sourceOrigine,
        String sourceUrl,
        ProductStatus statut,
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
        List<ProductImageResponse> images = product.getImages() == null ? List.of()
                : product.getImages().stream()
                .sorted(Comparator.comparingInt(ProductImage::getOrdre).thenComparing(ProductImage::getId))
                .map(img -> new ProductImageResponse(img.getId(), toMediaUrl(img.getRelativePath()), img.getOrdre()))
                .toList();
        List<ProductVideoResponse> videos = product.getVideos() == null ? List.of()
                : product.getVideos().stream()
                .sorted(Comparator.comparingInt(ProductVideo::getOrdre).thenComparing(ProductVideo::getId))
                .map(vid -> new ProductVideoResponse(
                        vid.getId(),
                        toMediaUrl(vid.getRelativePath()),
                        vid.getRelativePath(),
                        vid.getOrdre()))
                .toList();
        List<ProductAttributeResponse> attributs = product.getAttributes() == null ? List.of()
                : product.getAttributes().stream()
                .map(ProductResponse::toAttr)
                .toList();
        return new ProductResponse(
                product.getId(),
                product.getNom(),
                product.getDescription(),
                product.getPrix(),
                product.getStock(),
                product.getCategory().getId(),
                product.getCategory().getNom(),
                product.getSourceOrigine(),
                product.getSourceUrl(),
                product.getStatut(),
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

    private static ProductAttributeResponse toAttr(ProductAttributeValue value) {
        return new ProductAttributeResponse(
                value.getAttributeDefinition().getId(),
                value.getAttributeDefinition().getNomAttribut(),
                value.getAttributeDefinition().getTypeValeur(),
                value.getValeur()
        );
    }

    private static String toMediaUrl(String relativePath) {
        String path = relativePath.replace("\\", "/");
        return path.startsWith("/") ? "/media" + path : "/media/" + path;
    }
}
