package com.ndbshopping.backend.repository;

import com.ndbshopping.backend.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findByParentIsNullOrderByNomAsc();

    Optional<Category> findFirstByNomIgnoreCase(String nom);

    boolean existsByNomIgnoreCaseAndParent(String nom, Category parent);

    @Query("SELECT DISTINCT c FROM Category c LEFT JOIN FETCH c.parent")
    List<Category> findAllWithParent();

    @Query("SELECT COALESCE(MAX(c.ordreAffichage), -1) FROM Category c")
    int findMaxOrdreAffichage();

    boolean existsByParentId(Long parentId);
}
