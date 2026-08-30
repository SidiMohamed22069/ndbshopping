package com.ndbshopping.backend.controller;

import com.ndbshopping.backend.entity.User;
import com.ndbshopping.backend.entity.enums.Role;
import com.ndbshopping.backend.repository.UserRepository;
import com.ndbshopping.backend.service.OtpService;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

    private static final String SEED_ADMIN_PHONE = "37565537";

    @MockitoBean
    private RedisConnectionFactory redisConnectionFactory;

    @MockitoBean
    private OtpService otpService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void resetNonSeedUsers() {
        userRepository.findAll().stream()
                .filter(user -> !SEED_ADMIN_PHONE.equals(user.getTelephone()))
                .toList()
                .forEach(userRepository::delete);
    }

    @Test
    void login_verifiedAccount_returnsJwtWithoutOtp() throws Exception {
        mockMvc.perform(post("/api/auth/register-or-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"telephone":"37565537","password":"password123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.user.telephone").value(SEED_ADMIN_PHONE))
                .andExpect(jsonPath("$.user.role").value("ADMIN"))
                .andExpect(jsonPath("$.user.telephoneVerifie").value(true));

        verify(otpService, never()).sendOtp(anyString(), anyString());
    }

    @Test
    void login_verifiedAccount_wrongPassword_returns401() throws Exception {
        mockMvc.perform(post("/api/auth/register-or-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"telephone":"37565537","password":"wrong-password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Mot de passe incorrect"));

        verify(otpService, never()).sendOtp(anyString(), anyString());
    }

    @Test
    void login_unknownTelephone_returns404() throws Exception {
        mockMvc.perform(post("/api/auth/register-or-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"telephone":"24009999","password":"secret12"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Aucun compte avec ce numéro, veuillez vous inscrire."));

        verify(otpService, never()).sendOtp(anyString(), anyString());
        assertFalse(userRepository.existsByTelephone("24009999"));
    }

    @Test
    void register_newTelephone_createsClientAndSendsOtp() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nom":"Nouveau Client","telephone":"24001111","password":"secret12"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Code envoyé"));

        User created = userRepository.findByTelephone("24001111").orElseThrow();
        assertEquals("Nouveau Client", created.getNom());
        assertEquals(Role.CLIENT, created.getRole());
        assertFalse(created.isTelephoneVerifie());
        assertTrue(passwordEncoder.matches("secret12", created.getPasswordHash()));
        verify(otpService).sendOtp(eq("24001111"), anyString());
    }

    @Test
    void register_existingTelephone_returns409() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nom":"Doublon","telephone":"37565537","password":"secret12"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Ce numéro de téléphone est déjà utilisé, veuillez vous connecter."));

        verify(otpService, never()).sendOtp(anyString(), anyString());
    }
}
