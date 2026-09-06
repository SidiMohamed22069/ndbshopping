package com.ndbshopping.backend.controller;

import com.ndbshopping.backend.dto.common.PageResponse;
import com.ndbshopping.backend.dto.favorite.AddFavoriteRequest;
import com.ndbshopping.backend.dto.favorite.FavoriteResponse;
import com.ndbshopping.backend.dto.favorite.FavoriteStatusResponse;
import com.ndbshopping.backend.security.CurrentUserService;
import com.ndbshopping.backend.service.FavoriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/favorites")
@Tag(name = "Favoris")
public class FavoriteController {

    private final FavoriteService favoriteService;
    private final CurrentUserService currentUserService;

    public FavoriteController(FavoriteService favoriteService, CurrentUserService currentUserService) {
        this.favoriteService = favoriteService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/me")
    @Operation(summary = "Mes produits favoris")
    public PageResponse<FavoriteResponse> mine(@PageableDefault(size = 20) Pageable pageable) {
        return favoriteService.listMine(currentUserService.requireUser(), pageable);
    }

    @GetMapping("/product/{productId}")
    @Operation(summary = "Ce produit est-il dans mes favoris ?")
    public FavoriteStatusResponse status(@PathVariable Long productId) {
        return new FavoriteStatusResponse(favoriteService.isFavorited(currentUserService.requireUser(), productId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ajoute un produit à mes favoris (idempotent)")
    public void add(@Valid @RequestBody AddFavoriteRequest request) {
        favoriteService.add(currentUserService.requireUser(), request.productId());
    }

    @DeleteMapping("/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Retire un produit de mes favoris")
    public void remove(@PathVariable Long productId) {
        favoriteService.remove(currentUserService.requireUser(), productId);
    }
}
