package com.ndbshopping.backend.service;

import com.ndbshopping.backend.dto.cart.CartItemInput;
import com.ndbshopping.backend.dto.cart.CartItemResponse;
import com.ndbshopping.backend.dto.cart.CartResponse;
import com.ndbshopping.backend.dto.cart.CartSyncRequest;
import com.ndbshopping.backend.entity.CartItem;
import com.ndbshopping.backend.entity.Product;
import com.ndbshopping.backend.entity.User;
import com.ndbshopping.backend.entity.enums.ProductStatus;
import com.ndbshopping.backend.exception.ApiException;
import com.ndbshopping.backend.repository.CartItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class CartService {

    private final CartItemRepository cartItemRepository;
    private final ProductService productService;

    public CartService(CartItemRepository cartItemRepository, ProductService productService) {
        this.cartItemRepository = cartItemRepository;
        this.productService = productService;
    }

    @Transactional(readOnly = true)
    public CartResponse getCart(User user) {
        List<CartItem> items = cartItemRepository.findByUserId(user.getId());
        items.forEach(item -> {
            item.getProduct().getCategory().getNom();
            item.getProduct().getImages().size();
            item.getProduct().getAttributes().size();
        });
        List<CartItemResponse> responses = items.stream().map(CartItemResponse::from).toList();
        BigDecimal total = responses.stream()
                .map(CartItemResponse::sousTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CartResponse(responses, total);
    }

    @Transactional
    public CartResponse sync(User user, CartSyncRequest request) {
        cartItemRepository.deleteByUserId(user.getId());
        for (CartItemInput input : request.items()) {
            Product product = requirePublished(input.productId());
            assertStock(product, input.quantite());
            cartItemRepository.save(CartItem.builder()
                    .user(user)
                    .product(product)
                    .quantite(input.quantite())
                    .build());
        }
        return getCart(user);
    }

    @Transactional
    public void clear(User user) {
        cartItemRepository.deleteByUserId(user.getId());
    }

    private Product requirePublished(Long productId) {
        Product product = productService.get(productId);
        if (product.getStatut() != ProductStatus.PUBLIE) {
            throw ApiException.badRequest("Le produit '" + product.getNom() + "' n'est pas disponible");
        }
        return product;
    }

    static void assertStock(Product product, int quantite) {
        if (product.getStock() != null && product.getStock() < quantite) {
            throw ApiException.badRequest("Stock insuffisant pour " + product.getNom());
        }
    }
}
