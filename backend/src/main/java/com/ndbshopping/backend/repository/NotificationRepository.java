package com.ndbshopping.backend.repository;

import com.ndbshopping.backend.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<Notification> findByLuOrderByCreatedAtDesc(boolean lu, Pageable pageable);

    long countByLuFalse();
}
