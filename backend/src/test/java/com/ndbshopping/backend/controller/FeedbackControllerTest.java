package com.ndbshopping.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ndbshopping.backend.entity.User;
import com.ndbshopping.backend.entity.enums.Role;
import com.ndbshopping.backend.repository.UserFeedbackRepository;
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

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FeedbackControllerTest {

    private static final String SEED_ADMIN_PHONE = "37565537";
    private static final String CLIENT_PHONE = "24007777";

    @MockitoBean
    private RedisConnectionFactory redisConnectionFactory;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserFeedbackRepository feedbackRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private User client;
    private User admin;

    @BeforeEach
    void setUp() {
        feedbackRepository.deleteAll();
        userRepository.findAll().stream()
                .filter(u -> !SEED_ADMIN_PHONE.equals(u.getTelephone()))
                .toList()
                .forEach(userRepository::delete);
        client = userRepository.save(User.builder()
                .nom("Client avis")
                .telephone(CLIENT_PHONE)
                .passwordHash(passwordEncoder.encode("secret12"))
                .telephoneVerifie(true)
                .role(Role.USER)
                .build());
        admin = userRepository.findByTelephone(SEED_ADMIN_PHONE).orElseThrow();
    }

    @AfterEach
    void tearDown() {
        feedbackRepository.deleteAll();
    }

    @Test
    void anonymousVisitor_canSubmitFeedback_withoutAuthentication() throws Exception {
        mockMvc.perform(post("/api/feedbacks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"category":"SUGGESTION","message":"Ajoutez un mode sombre.","contactInfo":"22200000000"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.anonymous").value(true))
                .andExpect(jsonPath("$.userId").doesNotExist())
                .andExpect(jsonPath("$.statut").value("NOUVEAU"))
                .andExpect(jsonPath("$.contactInfo").value("22200000000"));
    }

    @Test
    void authenticatedUser_submission_isLinkedToAccount() throws Exception {
        mockMvc.perform(post("/api/feedbacks")
                        .header("Authorization", bearer(client))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"category":"BUG","message":"La page produit plante sur mobile."}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.anonymous").value(false))
                .andExpect(jsonPath("$.userId").value(client.getId()));
    }

    @Test
    void blankMessage_isRejected() throws Exception {
        mockMvc.perform(post("/api/feedbacks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"category":"OTHER","message":""}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void repeatedSubmissions_fromSameIp_areRateLimited() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/feedbacks")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"category":"OTHER","message":"Avis numéro %d"}
                                    """.formatted(i)))
                    .andExpect(status().isCreated());
        }
        mockMvc.perform(post("/api/feedbacks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"category":"OTHER","message":"Avis en trop"}
                                """))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void adminList_requiresAdminRole() throws Exception {
        mockMvc.perform(get("/api/admin/feedbacks").header("Authorization", bearer(client)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminList_andStatusUpdate_workEndToEnd() throws Exception {
        mockMvc.perform(post("/api/feedbacks")
                        .header("Authorization", bearer(client))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"category":"FEATURE_REQUEST","message":"Un mode comparaison de produits svp."}
                                """))
                .andExpect(status().isCreated());

        String adminToken = bearer(admin);
        String body = mockMvc.perform(get("/api/admin/feedbacks").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].anonymous").value(false))
                .andExpect(jsonPath("$.content[0].userNom").value("Client avis"))
                .andReturn().getResponse().getContentAsString();
        Long feedbackId = objectMapper.readTree(body).get("content").get(0).get("id").asLong();

        mockMvc.perform(get("/api/admin/feedbacks/count-nouveaux").header("Authorization", adminToken))
                .andExpect(jsonPath("$.count").value(1));

        mockMvc.perform(patch("/api/admin/feedbacks/{id}/statut", feedbackId)
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"statut":"TRAITE"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("TRAITE"));

        mockMvc.perform(get("/api/admin/feedbacks/count-nouveaux").header("Authorization", adminToken))
                .andExpect(jsonPath("$.count").value(0));
    }

    private String bearer(User user) {
        return "Bearer " + jwtUtil.generateToken(user.getId(), user.getTelephone(), user.getRole().name());
    }
}
