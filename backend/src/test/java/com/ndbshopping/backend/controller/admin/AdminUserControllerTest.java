package com.ndbshopping.backend.controller.admin;

import com.ndbshopping.backend.entity.User;
import com.ndbshopping.backend.entity.enums.Role;
import com.ndbshopping.backend.repository.UserRepository;
import com.ndbshopping.backend.security.JwtUtil;
import com.ndbshopping.backend.service.OtpService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminUserControllerTest {

    private static final String SEED_ADMIN_PHONE = "37565537";
    private static final String SEED_ADMIN_PASSWORD = "password123";

    @MockitoBean
    private RedisConnectionFactory redisConnectionFactory;

    @MockitoBean
    private OtpService otpService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;

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
    void seeder_createsOnlyAdmin37565537_withWorkingPassword() throws Exception {
        User admin = userRepository.findByTelephone(SEED_ADMIN_PHONE).orElseThrow();
        assertEquals("Administrateur", admin.getNom());
        assertEquals(Role.ADMIN, admin.getRole());
        assertTrue(admin.isTelephoneVerifie());
        assertTrue(admin.isActif());
        assertTrue(passwordEncoder.matches(SEED_ADMIN_PASSWORD, admin.getPasswordHash()));
        assertFalse(userRepository.existsByTelephone("20000001"));
        assertEquals(1, userRepository.findByRoleOrderByCreatedAtDesc(Role.ADMIN, Pageable.unpaged()).getTotalElements());

        mockMvc.perform(post("/api/auth/register-or-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nom":"Administrateur","telephone":"37565537","password":"password123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.user.role").value("ADMIN"))
                .andExpect(jsonPath("$.user.telephone").value(SEED_ADMIN_PHONE))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist());
    }

    @Test
    void createAdmin_byExistingAdmin_succeeds() throws Exception {
        mockMvc.perform(post("/api/admin/users")
                        .header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nom":"Second Admin","telephone":"32001111","password":"secret12","role":"ADMIN"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nom").value("Second Admin"))
                .andExpect(jsonPath("$.telephone").value("32001111"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.telephoneVerifie").value(true))
                .andExpect(jsonPath("$.actif").value(true))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void createClient_byAdmin_succeeds() throws Exception {
        mockMvc.perform(post("/api/admin/users")
                        .header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nom":"Un Client","telephone":"42001111","password":"secret12","role":"CLIENT"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("CLIENT"))
                .andExpect(jsonPath("$.telephoneVerifie").value(true))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void createUser_duplicateTelephone_returns409() throws Exception {
        mockMvc.perform(post("/api/admin/users")
                        .header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nom":"Doublon","telephone":"37565537","password":"secret12","role":"CLIENT"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Ce numéro de téléphone est déjà utilisé"));
    }

    @Test
    void client_cannotAccessAdminUsers_returns403() throws Exception {
        User client = userRepository.save(User.builder()
                .nom("Client Test")
                .telephone("22001111")
                .passwordHash(passwordEncoder.encode("secret12"))
                .telephoneVerifie(true)
                .role(Role.CLIENT)
                .build());

        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", bearer(client)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/users")
                        .header("Authorization", bearer(client))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nom":"Intrus","telephone":"23001111","password":"secret12","role":"ADMIN"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void admin_cannotChangeOwnRole_returns400() throws Exception {
        User admin = seedAdmin();
        mockMvc.perform(patch("/api/admin/users/{id}/role", admin.getId())
                        .header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"CLIENT"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Vous ne pouvez pas modifier votre propre rôle"));
    }

    private String adminBearer() {
        return bearer(seedAdmin());
    }

    private User seedAdmin() {
        return userRepository.findByTelephone(SEED_ADMIN_PHONE).orElseThrow();
    }

    private String bearer(User user) {
        return "Bearer " + jwtUtil.generateToken(user.getId(), user.getTelephone(), user.getRole().name());
    }
}
