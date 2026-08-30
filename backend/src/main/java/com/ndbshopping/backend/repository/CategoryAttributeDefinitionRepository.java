package com.ndbshopping.backend.repository;

import com.ndbshopping.backend.entity.CategoryAttributeDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoryAttributeDefinitionRepository extends JpaRepository<CategoryAttributeDefinition, Long> {

    List<CategoryAttributeDefinition> findByCategoryIdOrderByIdAsc(Long categoryId);

    boolean existsByCategoryIdAndNomAttributIgnoreCase(Long categoryId, String nomAttribut);
}
