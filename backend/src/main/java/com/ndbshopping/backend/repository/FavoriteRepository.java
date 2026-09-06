package com.ndbshopping.backend.repository;

import com.ndbshopping.backend.entity.Favorite;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

    Page<Favorite> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    boolean existsByUserIdAndProductId(Long userId, Long productId);

    void deleteByUserIdAndProductId(Long userId, Long productId);
}
