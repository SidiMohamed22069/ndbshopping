package com.ndbshopping.backend.repository;

import com.ndbshopping.backend.entity.PriceNegotiation;
import com.ndbshopping.backend.entity.enums.NegotiationActor;
import com.ndbshopping.backend.entity.enums.PriceNegotiationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PriceNegotiationRepository extends JpaRepository<PriceNegotiation, Long> {

    Page<PriceNegotiation> findByUserIdOrderByUpdatedAtDesc(Long userId, Pageable pageable);

    Page<PriceNegotiation> findAllByOrderByUpdatedAtDesc(Pageable pageable);

    Page<PriceNegotiation> findByStatutOrderByUpdatedAtDesc(PriceNegotiationStatus statut, Pageable pageable);

    Optional<PriceNegotiation> findFirstByProductIdAndUserIdAndStatutInOrderByCreatedAtDesc(
            Long productId, Long userId, List<PriceNegotiationStatus> statuts);

    long countByStatutInAndDerniereActionPar(List<PriceNegotiationStatus> statuts, NegotiationActor derniereActionPar);
}
