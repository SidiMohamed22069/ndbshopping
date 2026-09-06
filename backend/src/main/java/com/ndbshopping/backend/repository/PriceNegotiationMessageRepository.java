package com.ndbshopping.backend.repository;

import com.ndbshopping.backend.entity.PriceNegotiationMessage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PriceNegotiationMessageRepository extends JpaRepository<PriceNegotiationMessage, Long> {
}
