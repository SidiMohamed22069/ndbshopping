package com.ndbshopping.backend.controller;

import com.ndbshopping.backend.dto.category.AttributeDefinitionResponse;
import com.ndbshopping.backend.dto.category.CategoryResponse;
import com.ndbshopping.backend.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@Tag(name = "Catégories (public)")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    @Operation(summary = "Arborescence des catégories")
    public List<CategoryResponse> tree() {
        return categoryService.tree();
    }

    @GetMapping("/{id}/attributes")
    @Operation(summary = "Attributs dynamiques d'une catégorie (pour formulaire produit)")
    public List<AttributeDefinitionResponse> attributes(@PathVariable Long id) {
        return categoryService.attributes(id);
    }
}
