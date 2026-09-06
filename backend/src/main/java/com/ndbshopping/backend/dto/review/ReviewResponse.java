package com.ndbshopping.backend.dto.review;

import com.ndbshopping.backend.entity.Review;

import java.time.Instant;

public record ReviewResponse(
        Long id,
        Long productId,
        Long userId,
        String userNom,
        Integer rating,
        String commentaire,
        Instant createdAt,
        Instant updatedAt
) {
    public static ReviewResponse from(Review review) {
        return new ReviewResponse(
                review.getId(),
                review.getProduct().getId(),
                review.getUser().getId(),
                review.getUser().getNom(),
                review.getRating(),
                review.getCommentaire(),
                review.getCreatedAt(),
                review.getUpdatedAt()
        );
    }
}
