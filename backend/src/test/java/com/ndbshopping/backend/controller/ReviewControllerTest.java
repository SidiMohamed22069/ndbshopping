package com.ndbshopping.backend.controller;

import com.ndbshopping.backend.entity.Category;
import com.ndbshopping.backend.entity.Product;
import com.ndbshopping.backend.entity.User;
import com.ndbshopping.backend.entity.enums.CategoryType;
import com.ndbshopping.backend.entity.enums.ProductSource;
import com.ndbshopping.backend.entity.enums.ProductStatus;
import com.ndbshopping.backend.entity.enums.Role;
import com.ndbshopping.backend.repository.CategoryRepository;
import com.ndbshopping.backend.repository.ProductRepository;
import com.ndbshopping.backend.repository.ReviewRepository;
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
class ReviewControllerTest {

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
    private ReviewRepository reviewRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User alice;
    private User bob;
    private Product product;

    @BeforeEach
    void setUp() {
        clean();
        alice = saveUser("Alice", "24007771");
        bob = saveUser("Bob", "24007772");
        Category category = categoryRepository.save(Category.builder()
                .nom("Review-Cat")
                .type(CategoryType.PRODUIT)
                .build());
        product = productRepository.save(Product.builder()
                .nom("Produit noté")
                .prix(new BigDecimal("1000.00"))
                .stock(5)
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
    void submitReview_thenAverageAndCountReflectIt() throws Exception {
        mockMvc.perform(post("/api/products/{id}/reviews", product.getId())
                        .header("Authorization", bearer(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rating":5,"commentaire":"Excellent produit"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rating").value(5));

        mockMvc.perform(post("/api/products/{id}/reviews", product.getId())
                        .header("Authorization", bearer(bob))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rating":3,"commentaire":"Correct"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/products/{id}/reviews", product.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAvis").value(2))
                .andExpect(jsonPath("$.noteMoyenne").value(4.0))
                .andExpect(jsonPath("$.avis.content", org.hamcrest.Matchers.hasSize(2)));
    }

    @Test
    void resubmittingReview_updatesInPlace_ratherThanDuplicating() throws Exception {
        mockMvc.perform(post("/api/products/{id}/reviews", product.getId())
                        .header("Authorization", bearer(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rating":2,"commentaire":"Décevant au début"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/products/{id}/reviews", product.getId())
                        .header("Authorization", bearer(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rating":4,"commentaire":"En fait très bien à l'usage"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/products/{id}/reviews", product.getId()))
                .andExpect(jsonPath("$.totalAvis").value(1))
                .andExpect(jsonPath("$.noteMoyenne").value(4.0))
                .andExpect(jsonPath("$.avis.content[0].commentaire").value("En fait très bien à l'usage"));
    }

    @Test
    void invalidRating_isRejected() throws Exception {
        mockMvc.perform(post("/api/products/{id}/reviews", product.getId())
                        .header("Authorization", bearer(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rating":7}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteMine_removesOnlyMyReview() throws Exception {
        mockMvc.perform(post("/api/products/{id}/reviews", product.getId())
                        .header("Authorization", bearer(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rating":5}
                                """))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/products/{id}/reviews", product.getId())
                        .header("Authorization", bearer(bob))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rating":1}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/products/{id}/reviews/me", product.getId())
                        .header("Authorization", bearer(alice)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/products/{id}/reviews", product.getId()))
                .andExpect(jsonPath("$.totalAvis").value(1))
                .andExpect(jsonPath("$.avis.content[0].userNom").value("Bob"));
    }

    private User saveUser(String nom, String telephone) {
        return userRepository.save(User.builder()
                .nom(nom)
                .telephone(telephone)
                .passwordHash(passwordEncoder.encode("secret12"))
                .telephoneVerifie(true)
                .role(Role.USER)
                .build());
    }

    private void clean() {
        reviewRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.findAll().stream()
                .filter(user -> !SEED_ADMIN_PHONE.equals(user.getTelephone()))
                .toList()
                .forEach(userRepository::delete);
    }

    private String bearer(User user) {
        return "Bearer " + jwtUtil.generateToken(user.getId(), user.getTelephone(), user.getRole().name());
    }
}
