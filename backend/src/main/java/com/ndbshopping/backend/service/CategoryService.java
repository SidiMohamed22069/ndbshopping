package com.ndbshopping.backend.service;

import com.ndbshopping.backend.dto.category.AttributeDefinitionRequest;
import com.ndbshopping.backend.dto.category.AttributeDefinitionResponse;
import com.ndbshopping.backend.dto.category.CategoryRequest;
import com.ndbshopping.backend.dto.category.CategoryResponse;
import com.ndbshopping.backend.entity.Category;
import com.ndbshopping.backend.entity.CategoryAttributeDefinition;
import com.ndbshopping.backend.exception.ApiException;
import com.ndbshopping.backend.repository.CategoryAttributeDefinitionRepository;
import com.ndbshopping.backend.repository.CategoryRepository;
import com.ndbshopping.backend.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryAttributeDefinitionRepository attributeRepository;
    private final ProductRepository productRepository;
    private final FileStorageService fileStorageService;

    public CategoryService(
            CategoryRepository categoryRepository,
            CategoryAttributeDefinitionRepository attributeRepository,
            ProductRepository productRepository,
            FileStorageService fileStorageService
    ) {
        this.categoryRepository = categoryRepository;
        this.attributeRepository = attributeRepository;
        this.productRepository = productRepository;
        this.fileStorageService = fileStorageService;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> tree() {
        List<Category> all = categoryRepository.findAllWithParent();
        Map<Long, List<Category>> byParent = all.stream()
                .filter(c -> c.getParent() != null)
                .collect(Collectors.groupingBy(c -> c.getParent().getId()));
        return all.stream()
                .filter(c -> c.getParent() == null)
                .sorted(affichageOrder())
                .map(c -> toTree(c, byParent))
                .toList();
    }

    private CategoryResponse toTree(Category category, Map<Long, List<Category>> byParent) {
        List<CategoryResponse> children = byParent.getOrDefault(category.getId(), List.of()).stream()
                .sorted(affichageOrder())
                .map(child -> toTree(child, byParent))
                .toList();
        return CategoryResponse.from(category, children);
    }

    private static Comparator<Category> affichageOrder() {
        return Comparator.comparingInt(Category::getOrdreAffichage)
                .thenComparing(Category::getNom, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Category::getId);
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        Category parent = resolveParent(request.parentId());
        Category saved = categoryRepository.save(Category.builder()
                .nom(request.nom().trim())
                .type(request.type())
                .parent(parent)
                .imageUrl(request.imageUrl())
                .ordreAffichage(categoryRepository.findMaxOrdreAffichage() + 1)
                .build());
        return CategoryResponse.leaf(saved);
    }

    @Transactional
    public void reorder(List<Long> ordreIds) {
        Set<Long> unique = new HashSet<>(ordreIds);
        if (unique.size() != ordreIds.size()) {
            throw ApiException.badRequest("La liste contient des identifiants en double");
        }
        List<Category> found = categoryRepository.findAllById(ordreIds);
        if (found.size() != ordreIds.size()) {
            throw ApiException.badRequest("Une ou plusieurs catégories sont introuvables");
        }
        Map<Long, Category> byId = found.stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));
        for (int i = 0; i < ordreIds.size(); i++) {
            byId.get(ordreIds.get(i)).setOrdreAffichage(i);
        }
    }

    @Transactional
    public CategoryResponse update(Long id, CategoryRequest request) {
        Category category = get(id);
        if (request.parentId() != null && request.parentId().equals(id)) {
            throw ApiException.badRequest("Une catégorie ne peut pas être son propre parent");
        }
        category.setNom(request.nom().trim());
        category.setType(request.type());
        category.setParent(resolveParent(request.parentId()));
        if (request.imageUrl() != null) {
            category.setImageUrl(request.imageUrl());
        }
        return CategoryResponse.leaf(category);
    }

    @Transactional
    public void delete(Long id) {
        Category category = get(id);
        if (categoryRepository.existsByParentId(id)) {
            throw ApiException.conflict("Impossible de supprimer une catégorie qui a des sous-catégories");
        }
        if (productRepository.existsByCategoryId(id)) {
            throw ApiException.conflict("Impossible de supprimer une catégorie qui contient des produits");
        }
        fileStorageService.deleteStoredImage(category.getImageUrl());
        categoryRepository.delete(category);
    }

    @Transactional
    public CategoryResponse uploadImage(Long id, MultipartFile file) {
        Category category = get(id);
        String previous = category.getImageUrl();
        String stored = fileStorageService.storeCategoryImage(id, file);
        fileStorageService.deleteStoredImage(previous);
        category.setImageUrl(stored);
        return CategoryResponse.leaf(category);
    }

    @Transactional(readOnly = true)
    public List<AttributeDefinitionResponse> attributes(Long categoryId) {
        get(categoryId);
        return attributeRepository.findByCategoryIdOrderByIdAsc(categoryId).stream()
                .map(AttributeDefinitionResponse::from)
                .toList();
    }

    @Transactional
    public AttributeDefinitionResponse addAttribute(Long categoryId, AttributeDefinitionRequest request) {
        Category category = get(categoryId);
        if (attributeRepository.existsByCategoryIdAndNomAttributIgnoreCase(categoryId, request.nomAttribut().trim())) {
            throw ApiException.conflict("Cet attribut existe déjà pour cette catégorie");
        }
        CategoryAttributeDefinition saved = attributeRepository.save(CategoryAttributeDefinition.builder()
                .category(category)
                .nomAttribut(request.nomAttribut().trim())
                .typeValeur(request.typeValeur())
                .build());
        return AttributeDefinitionResponse.from(saved);
    }

    @Transactional
    public void deleteAttribute(Long categoryId, Long attributeId) {
        CategoryAttributeDefinition def = attributeRepository.findById(attributeId)
                .orElseThrow(() -> ApiException.notFound("Attribut introuvable"));
        if (!def.getCategory().getId().equals(categoryId)) {
            throw ApiException.badRequest("Attribut n'appartenant pas à cette catégorie");
        }
        attributeRepository.delete(def);
    }

    public Category get(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Catégorie introuvable"));
    }

    private Category resolveParent(Long parentId) {
        if (parentId == null) {
            return null;
        }
        return get(parentId);
    }
}
