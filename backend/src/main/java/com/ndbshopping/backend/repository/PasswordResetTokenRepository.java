package com.ndbshopping.backend.repository;

import com.ndbshopping.backend.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findTopByUserIdAndUsedFalseOrderByIdDesc(Long userId);

    long countByUserIdAndCreatedAtAfter(Long userId, Instant after);
}
