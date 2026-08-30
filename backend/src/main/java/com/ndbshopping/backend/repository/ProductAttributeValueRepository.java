package com.ndbshopping.backend.repository;

import com.ndbshopping.backend.entity.ProductAttributeValue;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductAttributeValueRepository extends JpaRepository<ProductAttributeValue, Long> {

    void deleteByProductId(Long productId);
}
