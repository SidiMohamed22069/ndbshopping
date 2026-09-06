package com.ndbshopping.backend.controller;

import com.ndbshopping.backend.entity.Category;
import com.ndbshopping.backend.entity.User;
import com.ndbshopping.backend.entity.enums.CategoryType;
import com.ndbshopping.backend.repository.CategoryRepository;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Filtre catalogue public (recherche / tri / ville / état) — étape "Recherche et filtres".
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductCatalogFilterTest {

    private static final String SEED_ADMIN_PHONE = "37565537";

    @MockitoBean
    private RedisConnectionFactory redisConnectionFactory;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;

    private Category category;

    @BeforeEach
    void setUp() throws Exception {
        productRepository.deleteAll();
        category = categoryRepository.save(Category.builder()
                .nom("Filtre-Cat")
                .type(CategoryType.PRODUIT)
                .build());

        createPublished("Chaise Nouadhibou neuve", 5000, "NOUADHIBOU", "NEUF");
        createPublished("Table Nouakchott occasion", 15000, "NOUAKCHOTT", "OCCASION");
        createPublished("Armoire Zouerat neuve", 25000, "ZOUERAT", "NEUF");
    }

    @AfterEach
    void tearDown() {
        productRepository.deleteAll();
    }

    @Test
    void filterByVille_returnsOnlyMatchingCity() throws Exception {
        mockMvc.perform(get("/api/products").param("ville", "nouadhibou"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.content[0].nom").value("Chaise Nouadhibou neuve"))
                .andExpect(jsonPath("$.content[0].ville").value("NOUADHIBOU"));
    }

    @Test
    void filterByEtat_returnsOnlyMatchingCondition() throws Exception {
        mockMvc.perform(get("/api/products").param("etat", "OCCASION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.content[0].nom").value("Table Nouakchott occasion"));
    }

    @Test
    void combinedVilleAndEtatFilter_narrowsResults() throws Exception {
        mockMvc.perform(get("/api/products").param("ville", "ZOUERAT").param("etat", "NEUF"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.content[0].nom").value("Armoire Zouerat neuve"));

        mockMvc.perform(get("/api/products").param("ville", "ZOUERAT").param("etat", "OCCASION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", org.hamcrest.Matchers.hasSize(0)));
    }

    @Test
    void sortByPrixAsc_ordersFromCheapest() throws Exception {
        mockMvc.perform(get("/api/products").param("sort", "prix,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].nom").value("Chaise Nouadhibou neuve"))
                .andExpect(jsonPath("$.content[2].nom").value("Armoire Zouerat neuve"));
    }

    @Test
    void sortByPrixDesc_ordersFromMostExpensive() throws Exception {
        mockMvc.perform(get("/api/products").param("sort", "prix,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].nom").value("Armoire Zouerat neuve"))
                .andExpect(jsonPath("$.content[2].nom").value("Chaise Nouadhibou neuve"));
    }

    @Test
    void defaultSort_isMostRecentFirst() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].nom").value("Armoire Zouerat neuve"));
    }

    private void createPublished(String nom, int prix, String ville, String etat) throws Exception {
        mockMvc.perform(post("/api/admin/products")
                        .header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nom":"%s","prix":%d,"categoryId":%d,"statut":"PUBLIE","ville":"%s","etat":"%s"}
                                """.formatted(nom, prix, category.getId(), ville, etat)))
                .andExpect(status().isCreated());
        Thread.sleep(5);
    }

    private String adminBearer() {
        User admin = userRepository.findByTelephone(SEED_ADMIN_PHONE).orElseThrow();
        return "Bearer " + jwtUtil.generateToken(admin.getId(), admin.getTelephone(), admin.getRole().name());
    }
}
