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
import com.ndbshopping.backend.repository.OrderRepository;
import com.ndbshopping.backend.repository.ProductRepository;
import com.ndbshopping.backend.repository.UserRepository;
import com.ndbshopping.backend.security.JwtUtil;
import com.ndbshopping.backend.service.OtpService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderControllerTest {

    private static final String SEED_ADMIN_PHONE = "37565537";
    private static final String CLIENT_PHONE = "24003333";

    @MockitoBean
    private RedisConnectionFactory redisConnectionFactory;

    @MockitoBean
    private OtpService otpService;

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
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User client;
    private Product product;

    @BeforeEach
    void setUp() {
        cleanOrderRelated();
        client = userRepository.save(User.builder()
                .nom("Client NDB")
                .telephone(CLIENT_PHONE)
                .passwordHash(passwordEncoder.encode("secret12"))
                .telephoneVerifie(true)
                .role(Role.CLIENT)
                .build());
        Category category = categoryRepository.save(Category.builder()
                .nom("Commande-Test")
                .type(CategoryType.PRODUIT)
                .build());
        product = productRepository.save(Product.builder()
                .nom("Article commande")
                .description("Pour tests commande")
                .prix(new BigDecimal("200.00"))
                .stock(10)
                .category(category)
                .sourceOrigine(ProductSource.MANUEL)
                .statut(ProductStatus.PUBLIE)
                .build());
    }

    @AfterEach
    void tearDown() {
        cleanOrderRelated();
    }

    @Test
    void create_withoutVilleLivraison_succeedsWithNouadhibou() throws Exception {
        seedCart();

        mockMvc.perform(post("/api/orders")
                        .header("Authorization", bearer(client))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"adresseDetails":"Cité plage, Nouadhibou"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.villeLivraison").value(Order.VILLE_LIVRAISON))
                .andExpect(jsonPath("$.adresseDetails").value("Cité plage, Nouadhibou"));
    }

    @Test
    void create_withNouadhibou_succeeds() throws Exception {
        seedCart();

        mockMvc.perform(post("/api/orders")
                        .header("Authorization", bearer(client))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"villeLivraison":"Nouadhibou","adresseDetails":"Centre ville"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.villeLivraison").value("Nouadhibou"));
    }

    @Test
    void create_legacyEnumCities_areIgnoredAndStoredAsNouadhibou() throws Exception {
        seedCart();
        mockMvc.perform(post("/api/orders")
                        .header("Authorization", bearer(client))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"villeLivraison":"ZOUERAT","adresseDetails":"Ancien client Zouérat"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.villeLivraison").value("Nouadhibou"));

        seedCart();
        mockMvc.perform(post("/api/orders")
                        .header("Authorization", bearer(client))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"villeLivraison":"NOUAKCHOTT","adresseDetails":"Ancien client Nouakchott"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.villeLivraison").value("Nouadhibou"));
    }

    @Test
    void existingHistoricalCityValues_loadWithoutFailingHibernate() throws Exception {
        insertHistoricalOrder("ZOUERAT");
        insertHistoricalOrder("NOUAKCHOTT");
        insertHistoricalOrder("NOUADHIBOU");

        assertEquals("ZOUERAT", orderRepository.findAll().stream()
                .filter(o -> "ZOUERAT".equals(o.getVilleLivraison()))
                .findFirst().orElseThrow().getVilleLivraison());

        mockMvc.perform(get("/api/orders/me")
                        .header("Authorization", bearer(client)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].villeLivraison",
                        containsInAnyOrder("ZOUERAT", "NOUAKCHOTT", "NOUADHIBOU")));
    }

    private void insertHistoricalOrder(String villeLivraison) {
        jdbcTemplate.update("""
                INSERT INTO orders (user_id, ville_livraison, adresse_details, statut, total, created_at)
                VALUES (?, ?, 'Adresse historique', 'EN_ATTENTE', 100.00, CURRENT_TIMESTAMP)
                """, client.getId(), villeLivraison);
    }

    private void seedCart() {
        cartItemRepository.save(CartItem.builder()
                .user(client)
                .product(product)
                .quantite(1)
                .build());
    }

    private void cleanOrderRelated() {
        orderRepository.deleteAll();
        cartItemRepository.deleteAll();
        userRepository.findAll().stream()
                .filter(user -> !SEED_ADMIN_PHONE.equals(user.getTelephone()))
                .toList()
                .forEach(userRepository::delete);
    }

    private String bearer(User user) {
        return "Bearer " + jwtUtil.generateToken(user.getId(), user.getTelephone(), user.getRole().name());
    }
}
