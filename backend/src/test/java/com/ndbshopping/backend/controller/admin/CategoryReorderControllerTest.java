package com.ndbshopping.backend.controller.admin;

import com.ndbshopping.backend.entity.Category;
import com.ndbshopping.backend.entity.User;
import com.ndbshopping.backend.entity.enums.CategoryType;
import com.ndbshopping.backend.entity.enums.Role;
import com.ndbshopping.backend.repository.CategoryRepository;
import com.ndbshopping.backend.repository.UserRepository;
import com.ndbshopping.backend.security.JwtUtil;
import com.ndbshopping.backend.service.OtpService;
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

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CategoryReorderControllerTest {

    private static final String SEED_ADMIN_PHONE = "37565537";

    @MockitoBean
    private RedisConnectionFactory redisConnectionFactory;

    @MockitoBean
    private OtpService otpService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void reorder_updatesOrdreAffichage_andPublicListFollows() throws Exception {
        Category alpha = newCategory("Reorder Alpha");
        Category bravo = newCategory("Reorder Bravo");
        Category charlie = newCategory("Reorder Charlie");

        mockMvc.perform(patch("/api/admin/categories/reorder")
                        .header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ordreIdsJson(charlie.getId(), alpha.getId(), bravo.getId())))
                .andExpect(status().isNoContent());

        assertEquals(0, categoryRepository.findById(charlie.getId()).orElseThrow().getOrdreAffichage());
        assertEquals(1, categoryRepository.findById(alpha.getId()).orElseThrow().getOrdreAffichage());
        assertEquals(2, categoryRepository.findById(bravo.getId()).orElseThrow().getOrdreAffichage());

        List<String> expected = List.of("Reorder Charlie", "Reorder Alpha", "Reorder Bravo");
        assertEquals(expected, namesInOrder(getTree("/api/categories"), expected));
        assertEquals(expected, namesInOrder(getTree("/api/admin/categories"), expected));
    }

    @Test
    void reorder_unknownId_returns400_andDoesNotChangeExisting() throws Exception {
        Category keep = newCategory("Reorder Keep");
        keep.setOrdreAffichage(17);
        keep = categoryRepository.save(keep);
        int before = keep.getOrdreAffichage();

        mockMvc.perform(patch("/api/admin/categories/reorder")
                        .header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ordreIdsJson(keep.getId(), 9_999_999L)))
                .andExpect(status().isBadRequest());

        assertEquals(before, categoryRepository.findById(keep.getId()).orElseThrow().getOrdreAffichage());
    }

    @Test
    void reorder_asClient_returns403() throws Exception {
        Category category = newCategory("Reorder Client");
        User client = userRepository.save(User.builder()
                .nom("Client reorder")
                .telephone("48112233")
                .passwordHash(passwordEncoder.encode("secret12"))
                .telephoneVerifie(true)
                .role(Role.CLIENT)
                .build());

        mockMvc.perform(patch("/api/admin/categories/reorder")
                        .header("Authorization", bearer(client))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ordreIdsJson(category.getId())))
                .andExpect(status().isForbidden());
    }

    private Category newCategory(String nom) {
        return categoryRepository.save(Category.builder()
                .nom(nom)
                .type(CategoryType.PRODUIT)
                .build());
    }

    private String getTree(String path) throws Exception {
        var request = get(path);
        if (path.startsWith("/api/admin/")) {
            request = request.header("Authorization", adminBearer());
        }
        MvcResult result = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getContentAsString();
    }

    private static List<String> namesInOrder(String json, List<String> ofInterest) throws Exception {
        var mapper = new ObjectMapper();
        return StreamSupport.stream(mapper.readTree(json).spliterator(), false)
                .map(node -> node.get("nom").asText())
                .filter(ofInterest::contains)
                .toList();
    }

    private static String ordreIdsJson(Long... ids) {
        return "{\"ordreIds\":" + java.util.Arrays.toString(ids) + "}";
    }

    private String adminBearer() {
        return bearer(userRepository.findByTelephone(SEED_ADMIN_PHONE).orElseThrow());
    }

    private String bearer(User user) {
        return "Bearer " + jwtUtil.generateToken(user.getId(), user.getTelephone(), user.getRole().name());
    }
}
