package com.ndbshopping.backend.repository;

import com.ndbshopping.backend.entity.ProductVideo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductVideoRepository extends JpaRepository<ProductVideo, Long> {

    List<ProductVideo> findByProductId(Long productId);

    Optional<ProductVideo> findByIdAndProductId(Long id, Long productId);
}
