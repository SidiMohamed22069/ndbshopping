package com.ndbshopping.backend.service;

import com.ndbshopping.backend.dto.common.PageResponse;
import com.ndbshopping.backend.dto.review.ProductReviewsResponse;
import com.ndbshopping.backend.dto.review.ReviewRequest;
import com.ndbshopping.backend.dto.review.ReviewResponse;
import com.ndbshopping.backend.entity.Product;
import com.ndbshopping.backend.entity.Review;
import com.ndbshopping.backend.entity.User;
import com.ndbshopping.backend.repository.ReviewRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ProductService productService;

    public ReviewService(ReviewRepository reviewRepository, ProductService productService) {
        this.reviewRepository = reviewRepository;
        this.productService = productService;
    }

    /** Un avis par (produit, utilisateur) : une nouvelle soumission met à jour l'avis existant. */
    @Transactional
    public ReviewResponse upsert(User user, Long productId, ReviewRequest request) {
        Product product = productService.get(productId);
        Review review = reviewRepository.findByProductIdAndUserId(productId, user.getId())
                .orElseGet(() -> Review.builder().product(product).user(user).build());
        review.setRating(request.rating());
        review.setCommentaire(request.commentaire());
        return ReviewResponse.from(reviewRepository.save(review));
    }

    @Transactional
    public void deleteMine(User user, Long productId) {
        reviewRepository.deleteByProductIdAndUserId(productId, user.getId());
    }

    @Transactional(readOnly = true)
    public ProductReviewsResponse listForProduct(Long productId, Pageable pageable) {
        productService.get(productId);
        Page<Review> page = reviewRepository.findByProductIdOrderByCreatedAtDescIdDesc(productId, pageable);
        Double moyenne = reviewRepository.averageRating(productId);
        long total = reviewRepository.countByProductId(productId);
        return new ProductReviewsResponse(moyenne, total, PageResponse.from(page.map(ReviewResponse::from)));
    }
}
