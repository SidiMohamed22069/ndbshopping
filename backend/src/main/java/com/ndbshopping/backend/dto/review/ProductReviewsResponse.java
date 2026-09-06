package com.ndbshopping.backend.dto.review;

import com.ndbshopping.backend.dto.common.PageResponse;

public record ProductReviewsResponse(
        Double noteMoyenne,
        long totalAvis,
        PageResponse<ReviewResponse> avis
) {
}
