package com.ndbshopping.backend.controller;

import com.ndbshopping.backend.entity.Category;
import com.ndbshopping.backend.entity.Product;
import com.ndbshopping.backend.entity.enums.CategoryType;
import com.ndbshopping.backend.entity.enums.ProductSource;
import com.ndbshopping.backend.entity.enums.ProductStatus;
import com.ndbshopping.backend.repository.CategoryRepository;
import com.ndbshopping.backend.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductControllerTest {

    @MockitoBean
    private RedisConnectionFactory redisConnectionFactory;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private Category categoryA;
    private Category categoryB;

    @BeforeEach
    void setUpCatalog() {
        productRepository.deleteAll();
        categoryA = categoryRepository.save(Category.builder()
                .nom("Filter-A")
                .type(CategoryType.PRODUIT)
                .build());
        categoryB = categoryRepository.save(Category.builder()
                .nom("Filter-B")
                .type(CategoryType.PRODUIT)
                .build());
        productRepository.save(product("Alpha Widget", "alpha desc", "100.00", categoryA));
        productRepository.save(product("Beta Gadget", "beta desc", "500.00", categoryA));
        productRepository.save(product("Gamma Widget", "gamma desc", "1000.00", categoryB));
    }

    @Test
    void list_withoutQueryParams_returnsOk() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content", hasSize(3)))
                .andExpect(jsonPath("$.page").value(0));
    }

    @Test
    void list_categoryIdOnly_filtersByCategory() throws Exception {
        mockMvc.perform(get("/api/products").param("categoryId", String.valueOf(categoryA.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[*].nom", containsInAnyOrder("Alpha Widget", "Beta Gadget")));
    }

    @Test
    void list_minPrixOnly_filtersByMinimumPrice() throws Exception {
        mockMvc.perform(get("/api/products").param("minPrix", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[*].nom", containsInAnyOrder("Beta Gadget", "Gamma Widget")));
    }

    @Test
    void list_maxPrixOnly_filtersByMaximumPrice() throws Exception {
        mockMvc.perform(get("/api/products").param("maxPrix", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[*].nom", containsInAnyOrder("Alpha Widget", "Beta Gadget")));
    }

    @Test
    void list_minAndMaxPrix_filtersByPriceRange() throws Exception {
        mockMvc.perform(get("/api/products")
                        .param("minPrix", "400")
                        .param("maxPrix", "600"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].nom").value("Beta Gadget"));
    }

    @Test
    void list_allFiltersCombined_returnsMatchingProduct() throws Exception {
        mockMvc.perform(get("/api/products")
                        .param("categoryId", String.valueOf(categoryA.getId()))
                        .param("minPrix", "50")
                        .param("maxPrix", "200")
                        .param("q", "widget"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].nom").value("Alpha Widget"));
    }

    @ParameterizedTest(name = "categoryId={0}, minPrix={1}, maxPrix={2}, q={3}")
    @MethodSource("filterCombinations")
    void list_optionalFilterCombinations_returnOk(
            boolean withCategoryId,
            boolean withMinPrix,
            boolean withMaxPrix,
            boolean withQ
    ) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/products");
        if (withCategoryId) {
            request.param("categoryId", String.valueOf(categoryA.getId()));
        }
        if (withMinPrix) {
            request.param("minPrix", "100");
        }
        if (withMaxPrix) {
            request.param("maxPrix", "2000");
        }
        if (withQ) {
            request.param("q", "Widget");
        }

        mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content").isArray());
    }

    static Stream<Arguments> filterCombinations() {
        List<Arguments> combinations = new ArrayList<>();
        boolean[] flags = {false, true};
        for (boolean categoryId : flags) {
            for (boolean minPrix : flags) {
                for (boolean maxPrix : flags) {
                    for (boolean q : flags) {
                        combinations.add(Arguments.of(categoryId, minPrix, maxPrix, q));
                    }
                }
            }
        }
        return combinations.stream();
    }

    private static Product product(String nom, String description, String prix, Category category) {
        return Product.builder()
                .nom(nom)
                .description(description)
                .prix(new BigDecimal(prix))
                .stock(10)
                .category(category)
                .sourceOrigine(ProductSource.MANUEL)
                .statut(ProductStatus.PUBLIE)
                .build();
    }

    @Test
    void get_publicDetail_neverExposesSupplierSourceUrl() throws Exception {
        Product external = productRepository.save(Product.builder()
                .nom("Sourced Product Detail")
                .description("desc")
                .prix(new BigDecimal("250.00"))
                .stock(5)
                .category(categoryA)
                .sourceOrigine(ProductSource.ALIBABA)
                .sourceUrl("https://www.alibaba.com/product-detail/example.html")
                .statut(ProductStatus.PUBLIE)
                .build());

        mockMvc.perform(get("/api/products/" + external.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceUrl").doesNotExist())
                .andExpect(jsonPath("$.externalSourced").doesNotExist())
                .andExpect(jsonPath("$.sourceOrigine").value("ALIBABA"));
    }

    @Test
    void list_public_neverExposesSupplierSourceUrl() throws Exception {
        productRepository.save(Product.builder()
                .nom("Sourced Product List")
                .description("desc")
                .prix(new BigDecimal("250.00"))
                .stock(5)
                .category(categoryA)
                .sourceOrigine(ProductSource.ALIBABA)
                .sourceUrl("https://www.alibaba.com/product-detail/example.html")
                .statut(ProductStatus.PUBLIE)
                .build());

        mockMvc.perform(get("/api/products").param("q", "Sourced Product List"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].sourceUrl").doesNotExist())
                .andExpect(jsonPath("$.content[0].externalSourced").doesNotExist());
    }
}
