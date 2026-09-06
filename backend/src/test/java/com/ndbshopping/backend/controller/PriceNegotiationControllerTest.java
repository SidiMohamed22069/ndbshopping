package com.ndbshopping.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ndbshopping.backend.entity.Category;
import com.ndbshopping.backend.entity.Product;
import com.ndbshopping.backend.entity.User;
import com.ndbshopping.backend.entity.enums.CategoryType;
import com.ndbshopping.backend.entity.enums.ProductSource;
import com.ndbshopping.backend.entity.enums.ProductStatus;
import com.ndbshopping.backend.entity.enums.Role;
import com.ndbshopping.backend.repository.CartItemRepository;
import com.ndbshopping.backend.repository.CategoryRepository;
import com.ndbshopping.backend.repository.OrderRepository;
import com.ndbshopping.backend.repository.PriceNegotiationRepository;
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
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PriceNegotiationControllerTest {

    private static final String SEED_ADMIN_PHONE = "37565537";
    private static final String BUYER_PHONE = "24009991";
    private static final String OTHER_PHONE = "24009992";

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
    private PriceNegotiationRepository negotiationRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private User buyer;
    private User other;
    private Product product;

    @BeforeEach
    void setUp() {
        clean();
        buyer = saveUser("Acheteur Nego", BUYER_PHONE);
        other = saveUser("Autre Acheteur", OTHER_PHONE);
        Category category = categoryRepository.save(Category.builder()
                .nom("Negociation-Cat")
                .type(CategoryType.PRODUIT)
                .build());
        product = productRepository.save(Product.builder()
                .nom("Canape a negocier")
                .description("Test negociation")
                .prix(new BigDecimal("10000.00"))
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
    void fullNegotiationFlow_endsWithCartAndOrderAtAgreedPrice() throws Exception {
        MvcResult started = mockMvc.perform(post("/api/negotiations")
                        .header("Authorization", bearer(buyer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":%d,"proposedPrice":7000,"message":"Interesse a ce prix"}
                                """.formatted(product.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value("PENDING"))
                .andExpect(jsonPath("$.proposedPrice").value(7000.00))
                .andExpect(jsonPath("$.derniereActionPar").value("USER"))
                .andExpect(jsonPath("$.messages", org.hamcrest.Matchers.hasSize(1)))
                .andReturn();
        Long negotiationId = objectMapper.readTree(started.getResponse().getContentAsString()).get("id").asLong();

        // Un autre utilisateur ne doit pas pouvoir voir cette négociation.
        mockMvc.perform(get("/api/negotiations/{id}", negotiationId)
                        .header("Authorization", bearer(other)))
                .andExpect(status().isForbidden());

        // L'admin contre-propose.
        mockMvc.perform(post("/api/admin/negotiations/{id}/offers", negotiationId)
                        .header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"price":8500,"message":"On peut faire 8500"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("COUNTER_OFFER"))
                .andExpect(jsonPath("$.proposedPrice").value(8500.00))
                .andExpect(jsonPath("$.derniereActionPar").value("ADMIN"));

        // L'admin ne peut pas accepter sa propre contre-offre.
        mockMvc.perform(patch("/api/admin/negotiations/{id}/accept", negotiationId)
                        .header("Authorization", adminBearer()))
                .andExpect(status().isBadRequest());

        // Le client accepte la contre-offre de l'admin.
        mockMvc.perform(patch("/api/negotiations/{id}/accept", negotiationId)
                        .header("Authorization", bearer(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("ACCEPTED"))
                .andExpect(jsonPath("$.proposedPrice").value(8500.00));

        // Le client ajoute le produit au panier au prix négocié.
        mockMvc.perform(post("/api/cart/sync")
                        .header("Authorization", bearer(buyer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"productId":%d,"quantite":1,"negotiationId":%d}]}
                                """.formatted(product.getId(), negotiationId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].prixUnitaire").value(8500.00))
                .andExpect(jsonPath("$.items[0].prixNegocie").value(true))
                .andExpect(jsonPath("$.total").value(8500.00));

        // La commande fige bien le prix négocié, pas le prix catalogue (10000).
        mockMvc.perform(post("/api/orders")
                        .header("Authorization", bearer(buyer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"adresseDetails":"Quartier plage"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.total").value(8500.00))
                .andExpect(jsonPath("$.items[0].prixUnitaire").value(8500.00));
    }

    @Test
    void adminCanAcceptUserProposal_directly() throws Exception {
        MvcResult started = mockMvc.perform(post("/api/negotiations")
                        .header("Authorization", bearer(buyer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":%d,"proposedPrice":9000}
                                """.formatted(product.getId())))
                .andExpect(status().isCreated())
                .andReturn();
        Long negotiationId = objectMapper.readTree(started.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(patch("/api/admin/negotiations/{id}/accept", negotiationId)
                        .header("Authorization", adminBearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("ACCEPTED"))
                .andExpect(jsonPath("$.proposedPrice").value(9000.00));
    }

    @Test
    void reject_closesNegotiation_andBlocksFurtherOffers() throws Exception {
        MvcResult started = mockMvc.perform(post("/api/negotiations")
                        .header("Authorization", bearer(buyer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":%d,"proposedPrice":3000}
                                """.formatted(product.getId())))
                .andExpect(status().isCreated())
                .andReturn();
        Long negotiationId = objectMapper.readTree(started.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(patch("/api/admin/negotiations/{id}/reject", negotiationId)
                        .header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"Prix trop bas"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("REJECTED"));

        mockMvc.perform(post("/api/negotiations/{id}/offers", negotiationId)
                        .header("Authorization", bearer(buyer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"price":3500}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cartSync_ignoresNegotiationId_whenNotAccepted() throws Exception {
        MvcResult started = mockMvc.perform(post("/api/negotiations")
                        .header("Authorization", bearer(buyer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":%d,"proposedPrice":7000}
                                """.formatted(product.getId())))
                .andExpect(status().isCreated())
                .andReturn();
        Long negotiationId = objectMapper.readTree(started.getResponse().getContentAsString()).get("id").asLong();

        // Toujours PENDING : le prix négocié ne doit pas s'appliquer.
        mockMvc.perform(post("/api/cart/sync")
                        .header("Authorization", bearer(buyer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"productId":%d,"quantite":1,"negotiationId":%d}]}
                                """.formatted(product.getId(), negotiationId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].prixUnitaire").value(10000.00))
                .andExpect(jsonPath("$.items[0].prixNegocie").value(false));
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
        orderRepository.deleteAll();
        cartItemRepository.deleteAll();
        negotiationRepository.deleteAll();
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
