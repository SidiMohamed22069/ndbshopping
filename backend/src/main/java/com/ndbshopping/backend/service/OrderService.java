package com.ndbshopping.backend.service;

import com.ndbshopping.backend.dto.common.PageResponse;
import com.ndbshopping.backend.dto.order.CreateOrderRequest;
import com.ndbshopping.backend.dto.order.OrderResponse;
import com.ndbshopping.backend.entity.CartItem;
import com.ndbshopping.backend.entity.Order;
import com.ndbshopping.backend.entity.OrderItem;
import com.ndbshopping.backend.entity.Product;
import com.ndbshopping.backend.entity.User;
import com.ndbshopping.backend.entity.enums.NotificationType;
import com.ndbshopping.backend.entity.enums.OrderStatus;
import com.ndbshopping.backend.exception.ApiException;
import com.ndbshopping.backend.repository.CartItemRepository;
import com.ndbshopping.backend.repository.OrderRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartItemRepository cartItemRepository;
    private final NotificationService notificationService;

    public OrderService(
            OrderRepository orderRepository,
            CartItemRepository cartItemRepository,
            NotificationService notificationService
    ) {
        this.orderRepository = orderRepository;
        this.cartItemRepository = cartItemRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public OrderResponse create(User user, CreateOrderRequest request) {
        if (!user.isTelephoneVerifie()) {
            throw ApiException.forbidden("Téléphone non vérifié");
        }
        List<CartItem> cartItems = cartItemRepository.findByUserId(user.getId());
        if (cartItems.isEmpty()) {
            throw ApiException.badRequest("Le panier est vide");
        }

        Order order = Order.builder()
                .user(user)
                .villeLivraison(Order.VILLE_LIVRAISON)
                .adresseDetails(request.adresseDetails().trim())
                .statut(OrderStatus.EN_ATTENTE)
                .total(BigDecimal.ZERO)
                .build();

        BigDecimal total = BigDecimal.ZERO;
        for (CartItem cartItem : cartItems) {
            Product product = cartItem.getProduct();
            CartService.assertStock(product, cartItem.getQuantite());
            if (product.getStock() != null) {
                product.setStock(product.getStock() - cartItem.getQuantite());
            }
            BigDecimal line = product.getPrix().multiply(BigDecimal.valueOf(cartItem.getQuantite()));
            total = total.add(line);
            order.getItems().add(OrderItem.builder()
                    .order(order)
                    .product(product)
                    .quantite(cartItem.getQuantite())
                    .prixUnitaire(product.getPrix())
                    .build());
        }
        order.setTotal(total);
        Order saved = orderRepository.save(order);
        cartItemRepository.deleteByUserId(user.getId());

        String message = "Nouvelle commande #" + saved.getId()
                + " — " + user.getNom()
                + " — " + total + " MRU"
                + " — " + saved.getVilleLivraison();
        notificationService.createAndPush(
                NotificationType.NOUVELLE_COMMANDE,
                message,
                "/orders/" + saved.getId()
        );

        touch(saved);
        return OrderResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> myOrders(User user, Pageable pageable) {
        Page<Order> page = orderRepository.findByUserIdOrderByCreatedAtDesc(user.getId(), pageable);
        page.forEach(this::touch);
        return PageResponse.from(page.map(OrderResponse::from));
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> adminSearch(OrderStatus statut, String ville, Pageable pageable) {
        Page<Order> page = orderRepository.search(statut, ville, pageable);
        page.forEach(this::touch);
        return PageResponse.from(page.map(OrderResponse::from));
    }

    @Transactional
    public OrderResponse updateStatus(Long id, OrderStatus statut) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Commande introuvable"));
        order.setStatut(statut);
        touch(order);
        return OrderResponse.from(order);
    }

    private void touch(Order order) {
        order.getUser().getNom();
        order.getItems().forEach(item -> item.getProduct().getNom());
    }
}
