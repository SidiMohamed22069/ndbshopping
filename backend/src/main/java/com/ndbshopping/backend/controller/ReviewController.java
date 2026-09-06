package com.ndbshopping.backend.controller;

import com.ndbshopping.backend.dto.review.ProductReviewsResponse;
import com.ndbshopping.backend.dto.review.ReviewRequest;
import com.ndbshopping.backend.dto.review.ReviewResponse;
import com.ndbshopping.backend.security.CurrentUserService;
import com.ndbshopping.backend.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products/{productId}/reviews")
@Tag(name = "Avis produits")
public class ReviewController {

    private final ReviewService reviewService;
    private final CurrentUserService currentUserService;

    public ReviewController(ReviewService reviewService, CurrentUserService currentUserService) {
        this.reviewService = reviewService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    @Operation(summary = "Avis publiés pour ce produit + note moyenne")
    public ProductReviewsResponse list(@PathVariable Long productId, @PageableDefault(size = 10) Pageable pageable) {
        return reviewService.listForProduct(productId, pageable);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Publie ou met à jour mon avis sur ce produit")
    public ReviewResponse submit(@PathVariable Long productId, @Valid @RequestBody ReviewRequest request) {
        return reviewService.upsert(currentUserService.requireUser(), productId, request);
    }

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Supprime mon avis sur ce produit")
    public void deleteMine(@PathVariable Long productId) {
        reviewService.deleteMine(currentUserService.requireUser(), productId);
    }
}
