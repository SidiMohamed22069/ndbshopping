package com.ndbshopping.backend.repository;

import com.ndbshopping.backend.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    Page<Review> findByProductIdOrderByCreatedAtDescIdDesc(Long productId, Pageable pageable);

    Optional<Review> findByProductIdAndUserId(Long productId, Long userId);

    long countByProductId(Long productId);

    void deleteByProductIdAndUserId(Long productId, Long userId);

    @Query("select avg(r.rating) from Review r where r.product.id = :productId")
    Double averageRating(Long productId);
}
