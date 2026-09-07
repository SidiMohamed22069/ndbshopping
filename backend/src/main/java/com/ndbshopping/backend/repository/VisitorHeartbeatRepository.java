package com.ndbshopping.backend.repository;

import com.ndbshopping.backend.entity.VisitorHeartbeat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface VisitorHeartbeatRepository extends JpaRepository<VisitorHeartbeat, Long> {

    Optional<VisitorHeartbeat> findByVisitorKey(String visitorKey);

    long countByAuthenticatedTrueAndLastSeenAtAfter(Instant since);

    long countByAuthenticatedFalseAndLastSeenAtAfter(Instant since);

    long countByLastSeenAtAfter(Instant since);

    void deleteByLastSeenAtBefore(Instant cutoff);
}
