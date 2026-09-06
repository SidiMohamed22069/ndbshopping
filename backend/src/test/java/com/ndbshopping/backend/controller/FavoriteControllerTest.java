package com.ndbshopping.backend.controller;

import com.ndbshopping.backend.entity.Category;
import com.ndbshopping.backend.entity.Product;
import com.ndbshopping.backend.entity.User;
import com.ndbshopping.backend.entity.enums.CategoryType;
import com.ndbshopping.backend.entity.enums.ProductSource;
import com.ndbshopping.backend.entity.enums.ProductStatus;
import com.ndbshopping.backend.entity.enums.Role;
import com.ndbshopping.backend.repository.CategoryRepository;
import com.ndbshopping.backend.repository.FavoriteRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FavoriteControllerTest {

    private static final String SEED_ADMIN_PHONE = "37565537";

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
    private FavoriteRepository favoriteRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User user;
    private Product product;

    @BeforeEach
    void setUp() {
        clean();
        user = userRepository.save(User.builder()
                .nom("Fan de shopping")
                .telephone("24006661")
                .passwordHash(passwordEncoder.encode("secret12"))
                .telephoneVerifie(true)
                .role(Role.USER)
                .build());
        Category category = categoryRepository.save(Category.builder()
                .nom("Favoris-Cat")
                .type(CategoryType.PRODUIT)
                .build());
        product = productRepository.save(Product.builder()
                .nom("Produit favori")
                .prix(new BigDecimal("2500.00"))
                .stock(3)
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
    void addThenList_showsProductInMyFavorites() throws Exception {
        mockMvc.perform(get("/api/favorites/product/{id}", product.getId()).header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.favorited").value(false));

        mockMvc.perform(post("/api/favorites")
                        .header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":%d}
                                """.formatted(product.getId())))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/favorites/product/{id}", product.getId()).header("Authorization", bearer(user)))
                .andExpect(jsonPath("$.favorited").value(true));

        mockMvc.perform(get("/api/favorites/me").header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.content[0].product.id").value(product.getId()));
    }

    @Test
    void addingTwice_isIdempotent() throws Exception {
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/favorites")
                            .header("Authorization", bearer(user))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"productId":%d}
                                    """.formatted(product.getId())))
                    .andExpect(status().isCreated());
        }
        mockMvc.perform(get("/api/favorites/me").header("Authorization", bearer(user)))
                .andExpect(jsonPath("$.content", org.hamcrest.Matchers.hasSize(1)));
    }

    @Test
    void remove_takesItOutOfFavorites() throws Exception {
        mockMvc.perform(post("/api/favorites")
                        .header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":%d}
                                """.formatted(product.getId())))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/favorites/{id}", product.getId()).header("Authorization", bearer(user)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/favorites/me").header("Authorization", bearer(user)))
                .andExpect(jsonPath("$.content", org.hamcrest.Matchers.hasSize(0)));
    }

    private void clean() {
        favoriteRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.findAll().stream()
                .filter(u -> !SEED_ADMIN_PHONE.equals(u.getTelephone()))
                .toList()
                .forEach(userRepository::delete);
    }

    private String bearer(User u) {
        return "Bearer " + jwtUtil.generateToken(u.getId(), u.getTelephone(), u.getRole().name());
    }
}
