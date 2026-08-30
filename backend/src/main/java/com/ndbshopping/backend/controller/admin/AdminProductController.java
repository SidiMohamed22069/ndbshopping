package com.ndbshopping.backend.controller.admin;

import com.ndbshopping.backend.dto.common.PageResponse;
import com.ndbshopping.backend.dto.product.CsvImportResponse;
import com.ndbshopping.backend.dto.product.ImportUrlRequest;
import com.ndbshopping.backend.dto.product.ProductImageResponse;
import com.ndbshopping.backend.dto.product.ProductRequest;
import com.ndbshopping.backend.dto.product.ProductResponse;
import com.ndbshopping.backend.dto.product.RejectProductRequest;
import com.ndbshopping.backend.entity.enums.ProductStatus;
import com.ndbshopping.backend.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/admin/products")
@Tag(name = "Admin — Produits")
public class AdminProductController {

    private final ProductService productService;

    public AdminProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    @Operation(summary = "Liste admin (y compris brouillons)")
    public PageResponse<ProductResponse> list(
            @RequestParam(required = false) ProductStatus statut,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) BigDecimal minPrix,
            @RequestParam(required = false) BigDecimal maxPrix,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return productService.searchAdmin(statut, categoryId, minPrix, maxPrix, q, pageable);
    }

    @GetMapping("/{id}")
    public ProductResponse get(@PathVariable Long id) {
        return productService.getAdmin(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(@Valid @RequestBody ProductRequest request) {
        return productService.create(request);
    }

    @PutMapping("/{id}")
    public ProductResponse update(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        return productService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        productService.delete(id);
    }

    @PatchMapping("/{id}/valider")
    @Operation(summary = "Valide un produit soumis (EN_ATTENTE → PUBLIE)")
    public ProductResponse validate(@PathVariable Long id) {
        return productService.validate(id);
    }

    @PatchMapping("/{id}/rejeter")
    @Operation(summary = "Rejette un produit soumis (EN_ATTENTE → REJETE)")
    public ProductResponse reject(@PathVariable Long id, @Valid @RequestBody RejectProductRequest request) {
        return productService.reject(id, request.raison());
    }

    @PostMapping(value = "/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Upload d'une image produit (jpg/png/webp, max 5 Mo)")
    public ProductImageResponse uploadImage(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        return productService.addImage(id, file);
    }

    @DeleteMapping("/{id}/images/{imageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteImage(@PathVariable Long id, @PathVariable Long imageId) {
        productService.deleteImage(id, imageId);
    }

    @PostMapping("/import/url")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Import assisté par URL (stub, crée un brouillon)")
    public ProductResponse importUrl(@Valid @RequestBody ImportUrlRequest request) {
        return productService.importFromUrl(request);
    }

    @PostMapping(value = "/import/csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Import CSV (colonnes: nom, description, prix, stock, categoryId)")
    public CsvImportResponse importCsv(@RequestParam("file") MultipartFile file) {
        return productService.importCsv(file);
    }
}
