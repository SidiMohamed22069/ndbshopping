package com.ndbshopping.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ndbshopping.backend.entity.Category;
import com.ndbshopping.backend.entity.User;
import com.ndbshopping.backend.entity.enums.CategoryType;
import com.ndbshopping.backend.entity.enums.Role;
import com.ndbshopping.backend.repository.CategoryRepository;
import com.ndbshopping.backend.repository.ProductRepository;
import com.ndbshopping.backend.repository.ProductVideoRepository;
import com.ndbshopping.backend.repository.UserRepository;
import com.ndbshopping.backend.security.JwtUtil;
import com.ndbshopping.backend.service.FileStorageService;
import com.ndbshopping.backend.service.OtpService;
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

import static org.hamcrest.Matchers.hasSize;
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
class ProductVideoControllerTest {

    private static final String SEED_ADMIN_PHONE = "37565537";
    private static final String CLIENT_PHONE = "24007771";

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
    private ProductVideoRepository productVideoRepository;

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
                .nom("Client Video")
                .telephone(CLIENT_PHONE)
                .passwordHash(passwordEncoder.encode("secret12"))
                .telephoneVerifie(true)
                .role(Role.CLIENT)
                .build());
        category = categoryRepository.save(Category.builder()
                .nom("Video-Cat")
                .type(CategoryType.PRODUIT)
                .build());
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    @Test
    void ownerCanUploadVideoWhilePending() throws Exception {
        Long productId = submitProduct("Produit video ok");

        mockMvc.perform(multipart("/api/products/{id}/videos", productId)
                        .file(mp4("clip.mp4"))
                        .header("Authorization", bearer(client)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.relativePath").isString())
                .andExpect(jsonPath("$.ordre").value(0))
                .andExpect(jsonPath("$.url").isString());
    }

    @Test
    void ownerCannotUploadVideoAfterValidation_returns403() throws Exception {
        Long productId = submitProduct("Produit video valide");
        mockMvc.perform(patch("/api/admin/products/{id}/valider", productId)
                        .header("Authorization", adminBearer()))
                .andExpect(status().isOk());

        mockMvc.perform(multipart("/api/products/{id}/videos", productId)
                        .file(mp4("late.mp4"))
                        .header("Authorization", bearer(client)))
                .andExpect(status().isForbidden());
    }

    @Test
    void uploadRejectedIfNotMp4OrWebm() throws Exception {
        Long productId = submitProduct("Produit video format");

        mockMvc.perform(multipart("/api/products/{id}/videos", productId)
                        .file(new MockMultipartFile(
                                "video",
                                "clip.mp4",
                                "video/mp4",
                                new byte[]{(byte) 0xFF, (byte) 0xD8, 1, 2, 3, 4, 5, 6, 7, 8}))
                        .header("Authorization", bearer(client)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Format de vidéo non autorisé (mp4, webm uniquement)"));
    }

    @Test
    void uploadRejectedIfLargerThan20Mo() throws Exception {
        Long productId = submitProduct("Produit video gros");
        byte[] huge = new byte[(int) FileStorageService.MAX_VIDEO_BYTES + 1];
        huge[4] = 'f';
        huge[5] = 't';
        huge[6] = 'y';
        huge[7] = 'p';

        mockMvc.perform(multipart("/api/products/{id}/videos", productId)
                        .file(new MockMultipartFile("video", "big.mp4", "video/mp4", huge))
                        .header("Authorization", bearer(client)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Fichier trop volumineux (max 20 Mo)"));
    }

    @Test
    void uploadRejectedBeyondTwoVideos() throws Exception {
        Long productId = submitProduct("Produit video limite");
        mockMvc.perform(multipart("/api/products/{id}/videos", productId)
                        .file(mp4("one.mp4"))
                        .header("Authorization", bearer(client)))
                .andExpect(status().isCreated());
        mockMvc.perform(multipart("/api/products/{id}/videos", productId)
                        .file(webm("two.webm"))
                        .header("Authorization", bearer(client)))
                .andExpect(status().isCreated());

        mockMvc.perform(multipart("/api/products/{id}/videos", productId)
                        .file(mp4("three.mp4"))
                        .header("Authorization", bearer(client)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error")
                        .value("Limite de 2 vidéos atteinte. Supprimez-en une pour en ajouter une autre."));
    }

    @Test
    void getProduct_returnsImagesAndVideosSeparately() throws Exception {
        Long productId = submitProduct("Produit media mixte");
        mockMvc.perform(multipart("/api/products/{id}/images", productId)
                        .file(new MockMultipartFile(
                                "file", "photo.jpg", MediaType.IMAGE_JPEG_VALUE,
                                new byte[]{(byte) 0xFF, (byte) 0xD8, 1, 2, 3}))
                        .header("Authorization", bearer(client)))
                .andExpect(status().isCreated());
        mockMvc.perform(multipart("/api/products/{id}/videos", productId)
                        .file(mp4("clip.mp4"))
                        .header("Authorization", bearer(client)))
                .andExpect(status().isCreated());

        mockMvc.perform(patch("/api/admin/products/{id}/valider", productId)
                        .header("Authorization", adminBearer()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/products/{id}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.images", hasSize(1)))
                .andExpect(jsonPath("$.videos", hasSize(1)))
                .andExpect(jsonPath("$.videos[0].relativePath").isString())
                .andExpect(jsonPath("$.aVideo").value(true));

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.nom=='Produit media mixte')].aVideo").exists());
    }

    @Test
    void deleteVideo_removesFileAndRow() throws Exception {
        Long productId = submitProduct("Produit video delete");
        MvcResult uploaded = mockMvc.perform(multipart("/api/products/{id}/videos", productId)
                        .file(mp4("gone.mp4"))
                        .header("Authorization", bearer(client)))
                .andExpect(status().isCreated())
                .andReturn();
        long videoId = objectMapper.readTree(uploaded.getResponse().getContentAsString()).get("id").asLong();
        String relativePath = objectMapper.readTree(uploaded.getResponse().getContentAsString())
                .get("relativePath").asText();
        assertTrue(Files.exists(fileStorageService.resolve(relativePath)));

        mockMvc.perform(delete("/api/products/{id}/videos/{videoId}", productId, videoId)
                        .header("Authorization", bearer(client)))
                .andExpect(status().isNoContent());

        assertFalse(productVideoRepository.findById(videoId).isPresent());
        assertFalse(Files.exists(fileStorageService.resolve(relativePath)));
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

    private static MockMultipartFile mp4(String filename) {
        return new MockMultipartFile("video", filename, "video/mp4", mp4Bytes());
    }

    private static MockMultipartFile webm(String filename) {
        return new MockMultipartFile("video", filename, "video/webm",
                new byte[]{0x1A, 0x45, (byte) 0xDF, (byte) 0xA3, 0, 1, 2, 3, 4, 5, 6, 7});
    }

    private static byte[] mp4Bytes() {
        return new byte[]{0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm', 0, 0, 0, 0};
    }
}
