package com.ndbshopping.backend.repository;

import com.ndbshopping.backend.entity.UserFeedback;
import com.ndbshopping.backend.entity.enums.FeedbackCategory;
import com.ndbshopping.backend.entity.enums.FeedbackStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface UserFeedbackRepository extends JpaRepository<UserFeedback, Long> {

    long countByStatut(FeedbackStatus statut);

    long countByIpAddressAndCreatedAtAfter(String ipAddress, Instant since);

    @Query("""
            SELECT f FROM UserFeedback f
            WHERE (:category IS NULL OR f.category = :category)
              AND (:statut IS NULL OR f.statut = :statut)
            ORDER BY f.createdAt DESC
            """)
    Page<UserFeedback> search(
            @Param("category") FeedbackCategory category,
            @Param("statut") FeedbackStatus statut,
            Pageable pageable
    );
}
