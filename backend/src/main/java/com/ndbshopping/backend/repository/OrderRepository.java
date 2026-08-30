package com.ndbshopping.backend.repository;

import com.ndbshopping.backend.entity.Order;
import com.ndbshopping.backend.entity.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Page<Order> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    boolean existsByUserId(Long userId);

    @Query("""
            SELECT o FROM Order o
            WHERE (:statut IS NULL OR o.statut = :statut)
              AND (:ville IS NULL OR o.villeLivraison = :ville)
            ORDER BY o.createdAt DESC
            """)
    Page<Order> search(
            @Param("statut") OrderStatus statut,
            @Param("ville") String ville,
            Pageable pageable
    );
}
