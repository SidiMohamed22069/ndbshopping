package com.ndbshopping.backend.service;

import com.ndbshopping.backend.dto.common.PageResponse;
import com.ndbshopping.backend.dto.favorite.FavoriteResponse;
import com.ndbshopping.backend.entity.Favorite;
import com.ndbshopping.backend.entity.Product;
import com.ndbshopping.backend.entity.User;
import com.ndbshopping.backend.repository.FavoriteRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final ProductService productService;

    public FavoriteService(FavoriteRepository favoriteRepository, ProductService productService) {
        this.favoriteRepository = favoriteRepository;
        this.productService = productService;
    }

    @Transactional
    public void add(User user, Long productId) {
        if (favoriteRepository.existsByUserIdAndProductId(user.getId(), productId)) {
            return;
        }
        Product product = productService.get(productId);
        favoriteRepository.save(Favorite.builder().user(user).product(product).build());
    }

    @Transactional
    public void remove(User user, Long productId) {
        favoriteRepository.deleteByUserIdAndProductId(user.getId(), productId);
    }

    @Transactional(readOnly = true)
    public boolean isFavorited(User user, Long productId) {
        return favoriteRepository.existsByUserIdAndProductId(user.getId(), productId);
    }

    @Transactional(readOnly = true)
    public PageResponse<FavoriteResponse> listMine(User user, Pageable pageable) {
        Page<Favorite> page = favoriteRepository.findByUserIdOrderByCreatedAtDesc(user.getId(), pageable);
        page.forEach(favorite -> {
            favorite.getProduct().getImages().size();
            favorite.getProduct().getCategory().getNom();
        });
        return PageResponse.from(page.map(FavoriteResponse::from));
    }
}
