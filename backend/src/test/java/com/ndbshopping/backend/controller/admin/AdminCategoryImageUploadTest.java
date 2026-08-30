package com.ndbshopping.backend.controller.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ndbshopping.backend.entity.Category;
import com.ndbshopping.backend.entity.Publication;
import com.ndbshopping.backend.entity.User;
import com.ndbshopping.backend.entity.enums.CategoryType;
import com.ndbshopping.backend.entity.enums.PublicationStatus;
import com.ndbshopping.backend.entity.enums.Role;
import com.ndbshopping.backend.repository.CategoryRepository;
import com.ndbshopping.backend.repository.PublicationRepository;
import com.ndbshopping.backend.repository.UserRepository;
import com.ndbshopping.backend.security.JwtUtil;
import com.ndbshopping.backend.service.FileStorageService;
import com.ndbshopping.backend.service.OtpService;
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
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminCategoryImageUploadTest {

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
    private PublicationRepository publicationRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void uploadCategoryImage_success_storesFileAndUpdatesImageUrl() throws Exception {
        Category category = newCategory("Upload-Cat");
        MockMultipartFile image = jpeg("image", "photo.jpg", new byte[]{(byte) 0xFF, (byte) 0xD8, 1, 2, 3});

        MvcResult result = mockMvc.perform(multipart("/api/admin/categories/{id}/image", category.getId())
                        .file(image)
                        .header("Authorization", adminBearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").isString())
                .andReturn();

        String imageUrl = jsonField(result, "imageUrl");
        assertTrue(imageUrl.startsWith("/media/categories/" + category.getId() + "/"));
        Path onDisk = fileStorageService.resolve(FileStorageService.toRelativePath(imageUrl));
        assertTrue(Files.exists(onDisk));
    }

    @Test
    void uploadCategoryImage_wrongType_returns400() throws Exception {
        Category category = newCategory("Bad-Type-Cat");
        MockMultipartFile image = new MockMultipartFile("image", "note.txt", "text/plain", "hello".getBytes());

        mockMvc.perform(multipart("/api/admin/categories/{id}/image", category.getId())
                        .file(image)
                        .header("Authorization", adminBearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Format d'image non autorisé (jpg, png, webp uniquement)"));
    }

    @Test
    void uploadCategoryImage_tooLarge_returns400() throws Exception {
        Category category = newCategory("Too-Large-Cat");
        byte[] huge = new byte[(int) FileStorageService.MAX_BYTES + 1];
        MockMultipartFile image = jpeg("image", "big.jpg", huge);

        mockMvc.perform(multipart("/api/admin/categories/{id}/image", category.getId())
                        .file(image)
                        .header("Authorization", adminBearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Fichier trop volumineux (max 5 Mo)"));
    }

    @Test
    void reuploadCategoryImage_deletesOldFile() throws Exception {
        Category category = newCategory("Reupload-Cat");

        MvcResult first = mockMvc.perform(multipart("/api/admin/categories/{id}/image", category.getId())
                        .file(jpeg("image", "one.jpg", new byte[]{(byte) 0xFF, (byte) 0xD8, 1}))
                        .header("Authorization", adminBearer()))
                .andExpect(status().isOk())
                .andReturn();
        String firstUrl = jsonField(first, "imageUrl");
        Path firstFile = fileStorageService.resolve(FileStorageService.toRelativePath(firstUrl));
        assertTrue(Files.exists(firstFile));

        MvcResult second = mockMvc.perform(multipart("/api/admin/categories/{id}/image", category.getId())
                        .file(jpeg("image", "two.jpg", new byte[]{(byte) 0xFF, (byte) 0xD8, 2, 3}))
                        .header("Authorization", adminBearer()))
                .andExpect(status().isOk())
                .andReturn();
        String secondUrl = jsonField(second, "imageUrl");
        Path secondFile = fileStorageService.resolve(FileStorageService.toRelativePath(secondUrl));

        assertFalse(Files.exists(firstFile));
        assertTrue(Files.exists(secondFile));
        assertFalse(firstUrl.equals(secondUrl));
        Category reloaded = categoryRepository.findById(category.getId()).orElseThrow();
        assertTrue(reloaded.getImageUrl().startsWith("categories/" + category.getId() + "/"));
    }

    @Test
    void uploadCategoryImage_clientForbidden() throws Exception {
        Category category = newCategory("Forbidden-Cat");
        User client = userRepository.save(User.builder()
                .nom("Client Image")
                .telephone("22002222")
                .passwordHash(passwordEncoder.encode("secret12"))
                .telephoneVerifie(true)
                .role(Role.CLIENT)
                .build());

        mockMvc.perform(multipart("/api/admin/categories/{id}/image", category.getId())
                        .file(jpeg("image", "photo.jpg", new byte[]{(byte) 0xFF, (byte) 0xD8, 1}))
                        .header("Authorization", bearer(client)))
                .andExpect(status().isForbidden());
    }

    @Test
    void uploadPublicationImage_success_storesFileAndUpdatesImageUrl() throws Exception {
        Publication publication = publicationRepository.save(Publication.builder()
                .titre("Actu upload")
                .contenu("Contenu")
                .statut(PublicationStatus.BROUILLON)
                .build());

        MvcResult result = mockMvc.perform(multipart("/api/admin/publications/{id}/image", publication.getId())
                        .file(jpeg("image", "cover.jpg", new byte[]{(byte) 0xFF, (byte) 0xD8, 9, 8}))
                        .header("Authorization", adminBearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").isString())
                .andReturn();

        String imageUrl = jsonField(result, "imageUrl");
        assertTrue(imageUrl.startsWith("/media/publications/" + publication.getId() + "/"));
        assertTrue(Files.exists(fileStorageService.resolve(FileStorageService.toRelativePath(imageUrl))));
    }

    private Category newCategory(String nom) {
        return categoryRepository.save(Category.builder()
                .nom(nom)
                .type(CategoryType.PRODUIT)
                .build());
    }

    private String adminBearer() {
        User admin = userRepository.findByTelephone("37565537").orElseThrow();
        return bearer(admin);
    }

    private String bearer(User user) {
        return "Bearer " + jwtUtil.generateToken(user.getId(), user.getTelephone(), user.getRole().name());
    }

    private static MockMultipartFile jpeg(String field, String filename, byte[] content) {
        return new MockMultipartFile(field, filename, MediaType.IMAGE_JPEG_VALUE, content);
    }

    private String jsonField(MvcResult result, String field) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get(field).asText();
    }
}
