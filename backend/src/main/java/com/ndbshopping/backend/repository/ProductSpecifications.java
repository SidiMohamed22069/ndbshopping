package com.ndbshopping.backend.repository;

import com.ndbshopping.backend.entity.Product;
import com.ndbshopping.backend.entity.enums.ProductStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ProductSpecifications {

    private ProductSpecifications() {
    }

    public static Specification<Product> matching(
            ProductStatus status,
            Long categoryId,
            BigDecimal minPrix,
            BigDecimal maxPrix,
            String q
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("statut"), status));
            }
            if (categoryId != null) {
                predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            }
            if (minPrix != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("prix"), minPrix));
            }
            if (maxPrix != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("prix"), maxPrix));
            }
            if (q != null && !q.isBlank()) {
                String pattern = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("nom")), pattern),
                        cb.like(cb.lower(cb.coalesce(root.get("description"), "")), pattern)
                ));
            }
            if (predicates.isEmpty()) {
                return cb.conjunction();
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
