package com.ndbshopping.backend.controller;

import com.ndbshopping.backend.entity.CartItem;
import com.ndbshopping.backend.entity.Category;
import com.ndbshopping.backend.entity.Order;
import com.ndbshopping.backend.entity.Product;
import com.ndbshopping.backend.entity.User;
import com.ndbshopping.backend.entity.enums.CategoryType;
import com.ndbshopping.backend.entity.enums.ProductSource;
import com.ndbshopping.backend.entity.enums.ProductStatus;
import com.ndbshopping.backend.entity.enums.Role;
import com.ndbshopping.backend.repository.CartItemRepository;
import com.ndbshopping.backend.repository.CategoryRepository;
import com.ndbshopping.backend.repository.NotificationRepository;
import com.ndbshopping.backend.repository.OrderRepository;
import com.ndbshopping.backend.repository.ProductRepository;
import com.ndbshopping.backend.repository.UserRepository;
import com.ndbshopping.backend.security.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationClientControllerTest {

    private static final String SEED_ADMIN_PHONE = "37565537";
    private static final String CLIENT_PHONE = "24008881";

    @MockitoBean
    private RedisConnectionFactory redisConnectionFactory;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User client;
    private Product product;

    @BeforeEach
    void setUp() {
        clean();
        client = userRepository.save(User.builder()
                .nom("Client Notif")
                .telephone(CLIENT_PHONE)
                .passwordHash(passwordEncoder.encode("secret12"))
                .telephoneVerifie(true)
                .role(Role.USER)
                .build());
        Category category = categoryRepository.save(Category.builder()
                .nom("Notif-Cat")
                .type(CategoryType.PRODUIT)
                .build());
        product = productRepository.save(Product.builder()
                .nom("Article notif")
                .prix(new BigDecimal("500.00"))
                .stock(10)
                .category(category)
                .sourceOrigine(ProductSource.MANUEL)
                .statut(ProductStatus.PUBLIE)
                .build());
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    @Test
    void orderStatusChange_notifiesOwnerOnly_notAnotherClient() throws Exception {
        User other = userRepository.save(User.builder()
                .nom("Autre client")
                .telephone("24008882")
                .passwordHash(passwordEncoder.encode("secret12"))
                .telephoneVerifie(true)
                .role(Role.USER)
                .build());

        cartItemRepository.save(CartItem.builder().user(client).product(product).quantite(1).build());
        var created = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/orders")
                        .header("Authorization", bearer(client))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"adresseDetails":"Quartier plage"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        Long orderId = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(patch("/api/admin/orders/{id}/statut", orderId)
                        .header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"statut":"CONFIRMEE"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/notifications/me").header("Authorization", bearer(client)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.content[0].type").value("COMMANDE_STATUT"));

        mockMvc.perform(get("/api/notifications/count-non-lues").header("Authorization", bearer(client)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));

        // L'autre client ne voit rien.
        mockMvc.perform(get("/api/notifications/me").header("Authorization", bearer(other)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", org.hamcrest.Matchers.hasSize(0)));
    }

    @Test
    void markAllAsRead_clearsUnreadCount() throws Exception {
        cartItemRepository.save(CartItem.builder().user(client).product(product).quantite(1).build());
        var created = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/orders")
                        .header("Authorization", bearer(client))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"adresseDetails":"Quartier plage"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        Long orderId = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(patch("/api/admin/orders/{id}/statut", orderId)
                        .header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"statut":"CONFIRMEE"}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/admin/orders/{id}/statut", orderId)
                        .header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"statut":"LIVREE"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/notifications/count-non-lues").header("Authorization", bearer(client)))
                .andExpect(jsonPath("$.count").value(2));

        mockMvc.perform(patch("/api/notifications/lire-tout").header("Authorization", bearer(client)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/notifications/count-non-lues").header("Authorization", bearer(client)))
                .andExpect(jsonPath("$.count").value(0));
    }

    private void clean() {
        notificationRepository.deleteAll();
        orderRepository.deleteAll();
        cartItemRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.findAll().stream()
                .filter(user -> !SEED_ADMIN_PHONE.equals(user.getTelephone()))
                .toList()
                .forEach(userRepository::delete);
    }

    private String adminBearer() {
        User admin = userRepository.findByTelephone(SEED_ADMIN_PHONE).orElseThrow();
        return "Bearer " + jwtUtil.generateToken(admin.getId(), admin.getTelephone(), admin.getRole().name());
    }

    private String bearer(User user) {
        return "Bearer " + jwtUtil.generateToken(user.getId(), user.getTelephone(), user.getRole().name());
    }
}
