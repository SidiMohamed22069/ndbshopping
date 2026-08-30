package com.ndbshopping.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ndbshopping.backend.entity.Category;
import com.ndbshopping.backend.entity.User;
import com.ndbshopping.backend.entity.enums.CategoryType;
import com.ndbshopping.backend.entity.enums.Role;
import com.ndbshopping.backend.repository.CategoryRepository;
import com.ndbshopping.backend.repository.ProductImageRepository;
import com.ndbshopping.backend.repository.ProductRepository;
import com.ndbshopping.backend.repository.UserRepository;
import com.ndbshopping.backend.security.JwtUtil;
import com.ndbshopping.backend.service.FileStorageService;
import com.ndbshopping.backend.service.OtpService;
import com.ndbshopping.backend.service.ProductService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductImageGalleryTest {

    private static final String SEED_ADMIN_PHONE = "37565537";
    private static final String CLIENT_PHONE = "24006661";

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
    private ProductImageRepository productImageRepository;

    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private User client;
    private Category category;

    @BeforeEach
    void setUp() {
        clean();
        client = userRepository.save(User.builder()
                .nom("Client Galerie")
                .telephone(CLIENT_PHONE)
                .passwordHash(passwordEncoder.encode("secret12"))
                .telephoneVerifie(true)
                .role(Role.CLIENT)
                .build());
        category = categoryRepository.save(Category.builder()
                .nom("Galerie-Cat")
                .type(CategoryType.PRODUIT)
                .build());
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    @Test
    void uploadSecondAndThirdImage_coexistWithoutReplacing() throws Exception {
        Long productId = submitProduct("Produit galerie");
        long firstId = upload(productId, "one.jpg");
        long secondId = upload(productId, "two.jpg");
        long thirdId = upload(productId, "three.jpg");

        assertEquals(3, productImageRepository.findByProductId(productId).size());

        mockMvc.perform(get("/api/products/me")
                        .header("Authorization", bearer(client)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].images", hasSize(3)))
                .andExpect(jsonPath("$.content[0].images[0].id").value(firstId))
                .andExpect(jsonPath("$.content[0].images[0].ordre").value(0))
                .andExpect(jsonPath("$.content[0].images[1].id").value(secondId))
                .andExpect(jsonPath("$.content[0].images[1].ordre").value(1))
                .andExpect(jsonPath("$.content[0].images[2].id").value(thirdId))
                .andExpect(jsonPath("$.content[0].images[2].ordre").value(2));
    }

    @Test
    void uploadBeyondSixImages_returns409() throws Exception {
        Long productId = submitProduct("Produit max images");
        for (int i = 0; i < ProductService.MAX_IMAGES_PAR_PRODUIT; i++) {
            upload(productId, "img-" + i + ".jpg");
        }

        mockMvc.perform(multipart("/api/products/{id}/images", productId)
                        .file(jpeg("overflow.jpg"))
                        .header("Authorization", bearer(client)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error")
                        .value("Limite de 6 images atteinte. Supprimez-en une pour en ajouter une autre."));

        assertEquals(6, productImageRepository.findByProductId(productId).size());
    }

    @Test
    void deleteOneImage_leavesOthersIntact() throws Exception {
        Long productId = submitProduct("Produit suppression");
        long firstId = upload(productId, "keep-a.jpg");
        long removedId = upload(productId, "remove-me.jpg");
        long thirdId = upload(productId, "keep-b.jpg");

        String removedUrl = productImageRepository.findById(removedId).orElseThrow().getRelativePath();
        var firstPath = fileStorageService.resolve(
                productImageRepository.findById(firstId).orElseThrow().getRelativePath());
        var thirdPath = fileStorageService.resolve(
                productImageRepository.findById(thirdId).orElseThrow().getRelativePath());
        assertTrue(Files.exists(fileStorageService.resolve(removedUrl)));

        mockMvc.perform(delete("/api/products/{id}/images/{imageId}", productId, removedId)
                        .header("Authorization", bearer(client)))
                .andExpect(status().isNoContent());

        assertFalse(productImageRepository.findById(removedId).isPresent());
        assertFalse(Files.exists(fileStorageService.resolve(removedUrl)));
        assertTrue(Files.exists(firstPath));
        assertTrue(Files.exists(thirdPath));
        assertEquals(2, productImageRepository.findByProductId(productId).size());
    }

    @Test
    void getProduct_returnsAllImagesInOrder() throws Exception {
        Long productId = submitProduct("Produit detail images");
        List<Long> ids = new ArrayList<>();
        ids.add(upload(productId, "a.jpg"));
        ids.add(upload(productId, "b.jpg"));
        ids.add(upload(productId, "c.jpg"));

        mockMvc.perform(patch("/api/admin/products/{id}/valider", productId)
                        .header("Authorization", adminBearer()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/products/{id}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.images", hasSize(3)))
                .andExpect(jsonPath("$.images[0].id").value(ids.get(0)))
                .andExpect(jsonPath("$.images[0].ordre").value(0))
                .andExpect(jsonPath("$.images[1].id").value(ids.get(1)))
                .andExpect(jsonPath("$.images[1].ordre").value(1))
                .andExpect(jsonPath("$.images[2].id").value(ids.get(2)))
                .andExpect(jsonPath("$.images[2].ordre").value(2));

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.nom=='Produit detail images')].images[0].id",
                        hasItem(ids.get(0).intValue())));
    }

    private Long submitProduct(String nom) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/products")
                        .header("Authorization", bearer(client))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nom":"%s","description":"Desc","prix":1500,"stock":2,"categoryId":%d}
                                """.formatted(nom, category.getId())))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private long upload(Long productId, String filename) throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/products/{id}/images", productId)
                        .file(jpeg(filename))
                        .header("Authorization", bearer(client)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.url").isString())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("id").asLong();
    }

    private void clean() {
        productRepository.deleteAll();
        userRepository.findAll().stream()
                .filter(user -> !SEED_ADMIN_PHONE.equals(user.getTelephone()))
                .toList()
                .forEach(userRepository::delete);
    }

    private String adminBearer() {
        return bearer(userRepository.findByTelephone(SEED_ADMIN_PHONE).orElseThrow());
    }

    private String bearer(User user) {
        return "Bearer " + jwtUtil.generateToken(user.getId(), user.getTelephone(), user.getRole().name());
    }

    private static MockMultipartFile jpeg(String filename) {
        return new MockMultipartFile("file", filename, MediaType.IMAGE_JPEG_VALUE,
                new byte[]{(byte) 0xFF, (byte) 0xD8, 1, 2, 3});
    }
}
