package com.ndbshopping.backend.controller.admin;

import com.ndbshopping.backend.entity.Category;
import com.ndbshopping.backend.entity.Product;
import com.ndbshopping.backend.entity.User;
import com.ndbshopping.backend.entity.enums.CategoryType;
import com.ndbshopping.backend.entity.enums.ProductSource;
import com.ndbshopping.backend.entity.enums.ProductStatus;
import com.ndbshopping.backend.repository.CategoryRepository;
import com.ndbshopping.backend.repository.ProductRepository;
import com.ndbshopping.backend.repository.UserRepository;
import com.ndbshopping.backend.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Vérifie le pendant admin de {@code ProductControllerTest#get_publicDetail_neverExposesSupplierSourceUrl} :
 * l'admin, lui, DOIT voir le lien fournisseur externe (Alibaba/AliExpress/Amazon) et le
 * drapeau calculé "externalSourced", pour pouvoir recommander l'article auprès du fournisseur.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminProductControllerTest {

    private static final String SEED_ADMIN_PHONE = "37565537";

    @MockitoBean
    private RedisConnectionFactory redisConnectionFactory;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;

    private Category category;

    @BeforeEach
    void setUp() {
        category = categoryRepository.save(Category.builder()
                .nom("Sourcing externe")
                .type(CategoryType.PRODUIT)
                .build());
    }

    @Test
    void get_adminDetail_exposesSourceUrlAndExternalSourcedFlag() throws Exception {
        Product external = productRepository.save(Product.builder()
                .nom("Produit Alibaba")
                .description("desc")
                .prix(new BigDecimal("999.00"))
                .stock(3)
                .category(category)
                .sourceOrigine(ProductSource.ALIBABA)
                .sourceUrl("https://www.alibaba.com/product-detail/example.html")
                .statut(ProductStatus.BROUILLON)
                .build());

        mockMvc.perform(get("/api/admin/products/" + external.getId())
                        .header("Authorization", adminBearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceUrl").value("https://www.alibaba.com/product-detail/example.html"))
                .andExpect(jsonPath("$.externalSourced").value(true));
    }

    @Test
    void get_adminDetail_manualProduct_externalSourcedIsFalse() throws Exception {
        Product manual = productRepository.save(Product.builder()
                .nom("Produit manuel")
                .description("desc")
                .prix(new BigDecimal("50.00"))
                .stock(3)
                .category(category)
                .sourceOrigine(ProductSource.MANUEL)
                .statut(ProductStatus.BROUILLON)
                .build());

        mockMvc.perform(get("/api/admin/products/" + manual.getId())
                        .header("Authorization", adminBearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceUrl").doesNotExist())
                .andExpect(jsonPath("$.externalSourced").value(false));
    }

    private String adminBearer() {
        User admin = userRepository.findByTelephone(SEED_ADMIN_PHONE).orElseThrow();
        return "Bearer " + jwtUtil.generateToken(admin.getId(), admin.getTelephone(), admin.getRole().name());
    }
}
