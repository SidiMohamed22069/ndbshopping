package com.ndbshopping.backend.repository;

import com.ndbshopping.backend.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    boolean existsByCategoryId(Long categoryId);

    boolean existsByNomIgnoreCase(String nom);

    Page<Product> findBySoumisPar_Id(Long userId, Pageable pageable);
}
