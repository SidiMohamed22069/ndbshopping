package com.ndbshopping.backend.repository;

import com.ndbshopping.backend.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // Boîte admin partagée (user IS NULL).
    Page<Notification> findByUserIsNullOrderByCreatedAtDesc(Pageable pageable);

    Page<Notification> findByUserIsNullAndLuOrderByCreatedAtDesc(boolean lu, Pageable pageable);

    long countByUserIsNullAndLuFalse();

    // Boîte privée d'un client.
    Page<Notification> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    long countByUserIdAndLuFalse(Long userId);

    @Modifying
    @Query("update Notification n set n.lu = true where n.user.id = :userId and n.lu = false")
    int markAllReadForUser(Long userId);
}
