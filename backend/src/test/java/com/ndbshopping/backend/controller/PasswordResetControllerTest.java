package com.ndbshopping.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ndbshopping.backend.entity.PasswordResetToken;
import com.ndbshopping.backend.entity.User;
import com.ndbshopping.backend.entity.enums.Role;
import com.ndbshopping.backend.repository.PasswordResetTokenRepository;
import com.ndbshopping.backend.repository.UserRepository;
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

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PasswordResetControllerTest {

    private static final String SEED_ADMIN_PHONE = "37565537";
    private static final String CLIENT_PHONE = "24009991";

    @MockitoBean
    private RedisConnectionFactory redisConnectionFactory;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private User client;

    @BeforeEach
    void setUp() {
        clean();
        client = userRepository.save(User.builder()
                .nom("Client Reset")
                .telephone(CLIENT_PHONE)
                .passwordHash(passwordEncoder.encode("ancienMdp1"))
                .telephoneVerifie(true)
                .role(Role.USER)
                .build());
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    @Test
    void request_forUnknownPhone_returns404() throws Exception {
        mockMvc.perform(post("/api/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"telephone":"29990000"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void request_createsTokenRow_andReturnsCodeForInAppDisplay() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"telephone":"%s"}
                                """.formatted(CLIENT_PHONE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").isString())
                .andExpect(jsonPath("$.expiresAt").isString())
                .andReturn();

        String code = objectMapper.readTree(result.getResponse().getContentAsString()).get("code").asText();
        assertEquals(6, code.length());

        List<PasswordResetToken> tokens = tokenRepository.findAll();
        assertEquals(1, tokens.size());
        PasswordResetToken saved = tokens.get(0);
        assertEquals(client.getId(), saved.getUser().getId());
        assertEquals(code, saved.getCode());
        assertTrue(saved.getExpiryDate().isAfter(Instant.now()));
        assertTrue(!saved.isUsed());
    }

    @Test
    void confirm_withCorrectCode_hashesNewPasswordInDb_andAllowsLoginWithIt() throws Exception {
        String code = requestCode();

        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"telephone":"%s","code":"%s","newPassword":"nouveauMdp1"}
                                """.formatted(CLIENT_PHONE, code)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").isString());

        User updated = userRepository.findByTelephone(CLIENT_PHONE).orElseThrow();
        assertTrue(passwordEncoder.matches("nouveauMdp1", updated.getPasswordHash()));
        assertTrue(!passwordEncoder.matches("ancienMdp1", updated.getPasswordHash()));

        PasswordResetToken token = tokenRepository.findAll().get(0);
        assertTrue(token.isUsed());

        mockMvc.perform(post("/api/auth/register-or-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"telephone":"%s","password":"nouveauMdp1"}
                                """.formatted(CLIENT_PHONE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString());
    }

    @Test
    void confirm_withWrongCode_isRejected_andDoesNotChangePassword() throws Exception {
        requestCode();

        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"telephone":"%s","code":"000000","newPassword":"nouveauMdp1"}
                                """.formatted(CLIENT_PHONE)))
                .andExpect(status().isBadRequest());

        User unchanged = userRepository.findByTelephone(CLIENT_PHONE).orElseThrow();
        assertTrue(passwordEncoder.matches("ancienMdp1", unchanged.getPasswordHash()));
    }

    @Test
    void confirm_withExpiredCode_isRejected() throws Exception {
        String code = requestCode();
        PasswordResetToken token = tokenRepository.findAll().get(0);
        token.setExpiryDate(Instant.now().minusSeconds(60));
        tokenRepository.save(token);

        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"telephone":"%s","code":"%s","newPassword":"nouveauMdp1"}
                                """.formatted(CLIENT_PHONE, code)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void confirm_lockoutAfterTooManyWrongAttempts() throws Exception {
        requestCode();

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/password-reset/confirm")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"telephone":"%s","code":"000000","newPassword":"nouveauMdp1"}
                                    """.formatted(CLIENT_PHONE)))
                    .andExpect(status().isBadRequest());
        }

        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"telephone":"%s","code":"000000","newPassword":"nouveauMdp1"}
                                """.formatted(CLIENT_PHONE)))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void request_isRateLimited_afterFiveRequestsInWindow() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/password-reset/request")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"telephone":"%s"}
                                    """.formatted(CLIENT_PHONE)))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(post("/api/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"telephone":"%s"}
                                """.formatted(CLIENT_PHONE)))
                .andExpect(status().isTooManyRequests());
    }

    private String requestCode() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"telephone":"%s"}
                                """.formatted(CLIENT_PHONE)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("code").asText();
    }

    private void clean() {
        tokenRepository.deleteAll();
        userRepository.findAll().stream()
                .filter(u -> !SEED_ADMIN_PHONE.equals(u.getTelephone()))
                .toList()
                .forEach(userRepository::delete);
    }
}
