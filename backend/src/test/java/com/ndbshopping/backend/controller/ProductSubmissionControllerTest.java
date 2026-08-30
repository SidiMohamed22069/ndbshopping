package com.ndbshopping.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ndbshopping.backend.dto.notification.NotificationResponse;
import com.ndbshopping.backend.entity.Category;
import com.ndbshopping.backend.entity.Notification;
import com.ndbshopping.backend.entity.Product;
import com.ndbshopping.backend.entity.User;
import com.ndbshopping.backend.entity.enums.CategoryType;
import com.ndbshopping.backend.entity.enums.NotificationType;
import com.ndbshopping.backend.entity.enums.ProductStatus;
import com.ndbshopping.backend.entity.enums.Role;
import com.ndbshopping.backend.repository.CategoryRepository;
import com.ndbshopping.backend.repository.NotificationRepository;
import com.ndbshopping.backend.repository.ProductRepository;
import com.ndbshopping.backend.repository.UserRepository;
import com.ndbshopping.backend.security.JwtUtil;
import com.ndbshopping.backend.service.NotificationService;
import com.ndbshopping.backend.service.OtpService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductSubmissionControllerTest {

    private static final String SEED_ADMIN_PHONE = "37565537";
    private static final String CLIENT_PHONE = "24005551";
    private static final String OTHER_CLIENT_PHONE = "24005552";
    private static final String PRODUCT_NOM = "Canape soumis unique";

    @MockitoBean
    private RedisConnectionFactory redisConnectionFactory;

    @MockitoBean
    private OtpService otpService;

    @MockitoSpyBean
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private User client;
    private User otherClient;
    private Category category;

    @BeforeEach
    void setUp() {
        clean();
        client = saveClient("Client Soumetteur", CLIENT_PHONE);
        otherClient = saveClient("Autre Client", OTHER_CLIENT_PHONE);
        category = categoryRepository.save(Category.builder()
                .nom("Soumission-Cat")
                .type(CategoryType.PRODUIT)
                .build());
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    @Test
    void clientSubmit_forcesEnAttente_evenIfPublieSent() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/products")
                        .header("Authorization", bearer(client))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitBody(PRODUCT_NOM, "PUBLIE")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value("EN_ATTENTE"))
                .andExpect(jsonPath("$.soumisParUserId").value(client.getId()))
                .andExpect(jsonPath("$.nom").value(PRODUCT_NOM))
                .andReturn();

        Long id = jsonId(result);
        Product saved = productRepository.findById(id).orElseThrow();
        assertEquals(ProductStatus.EN_ATTENTE, saved.getStatut());
    }

    @Test
    void pendingProduct_isHiddenFromPublicCatalog() throws Exception {
        mockMvc.perform(post("/api/products")
                        .header("Authorization", bearer(client))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitBody(PRODUCT_NOM, null)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].nom", not(hasItem(PRODUCT_NOM))));
    }

    @Test
    void submit_createsNotificationAndPushesWebsocket() throws Exception {
        mockMvc.perform(post("/api/products")
                        .header("Authorization", bearer(client))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitBody(PRODUCT_NOM, null)))
                .andExpect(status().isCreated());

        Notification notification = notificationRepository.findAll().stream()
                .filter(n -> n.getType() == NotificationType.PRODUIT_A_VALIDER)
                .findFirst()
                .orElseThrow();
        assertTrue(notification.getMessage().contains(PRODUCT_NOM));
        assertTrue(notification.getMessage().contains(client.getNom()));
        verify(messagingTemplate).convertAndSend(eq(NotificationService.ADMIN_TOPIC), any(NotificationResponse.class));
    }

    @Test
    void adminValidate_publishesToCatalog() throws Exception {
        Long id = submitAs(client, PRODUCT_NOM);

        mockMvc.perform(patch("/api/admin/products/{id}/valider", id)
                        .header("Authorization", adminBearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("PUBLIE"));

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].nom", hasItem(PRODUCT_NOM)));
    }

    @Test
    void adminReject_storesReason() throws Exception {
        Long id = submitAs(client, PRODUCT_NOM);

        mockMvc.perform(patch("/api/admin/products/{id}/rejeter", id)
                        .header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"raison":"Photos insuffisantes"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("REJETE"))
                .andExpect(jsonPath("$.raisonRejet").value("Photos insuffisantes"));

        Product saved = productRepository.findById(id).orElseThrow();
        assertEquals(ProductStatus.REJETE, saved.getStatut());
        assertEquals("Photos insuffisantes", saved.getRaisonRejet());

        mockMvc.perform(get("/api/products/me")
                        .header("Authorization", bearer(client)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].raisonRejet").value("Photos insuffisantes"));
    }

    @Test
    void validateOrReject_whenNotPending_returns400() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/admin/products")
                        .header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nom":"Brouillon admin","prix":10,"categoryId":%d}
                                """.formatted(category.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value("BROUILLON"))
                .andExpect(jsonPath("$.soumisParUserId").doesNotExist())
                .andReturn();
        Long brouillonId = jsonId(created);

        mockMvc.perform(patch("/api/admin/products/{id}/valider", brouillonId)
                        .header("Authorization", adminBearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Le produit n'est pas en attente de validation"));

        Long pendingId = submitAs(client, PRODUCT_NOM);
        mockMvc.perform(patch("/api/admin/products/{id}/valider", pendingId)
                        .header("Authorization", adminBearer()))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/admin/products/{id}/rejeter", pendingId)
                        .header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"raison":"Trop tard"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Le produit n'est pas en attente de validation"));
    }

    @Test
    void clientCannotValidateOrReject_returns403() throws Exception {
        Long id = submitAs(client, PRODUCT_NOM);

        mockMvc.perform(patch("/api/admin/products/{id}/valider", id)
                        .header("Authorization", bearer(client)))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/admin/products/{id}/rejeter", id)
                        .header("Authorization", bearer(client))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"raison":"Non"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void ownerCanUploadImageWhilePending_butNotAfterDecision() throws Exception {
        Long id = submitAs(client, PRODUCT_NOM);

        mockMvc.perform(multipart("/api/products/{id}/images", id)
                        .file(jpeg())
                        .header("Authorization", bearer(client)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.url").isString());

        mockMvc.perform(patch("/api/admin/products/{id}/valider", id)
                        .header("Authorization", adminBearer()))
                .andExpect(status().isOk());

        mockMvc.perform(multipart("/api/products/{id}/images", id)
                        .file(jpeg())
                        .header("Authorization", bearer(client)))
                .andExpect(status().isForbidden());
    }

    @Test
    void ownerCanMarkOwnPublishedProductAsSold() throws Exception {
        Long id = publishAs(client, PRODUCT_NOM);

        mockMvc.perform(patch("/api/products/{id}/vendu", id)
                        .header("Authorization", bearer(client)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("VENDU"));
    }

    @Test
    void ownerCannotMarkAnotherUsersProductAsSold() throws Exception {
        Long id = publishAs(otherClient, "Produit autre user");

        mockMvc.perform(patch("/api/products/{id}/vendu", id)
                        .header("Authorization", bearer(client)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanMarkAnyProductSoldOrArchived() throws Exception {
        Long soldId = publishAs(client, PRODUCT_NOM);
        Long archiveId = publishAs(otherClient, "A archiver par admin");

        mockMvc.perform(patch("/api/products/{id}/vendu", soldId)
                        .header("Authorization", adminBearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("VENDU"));

        mockMvc.perform(patch("/api/products/{id}/archiver", archiveId)
                        .header("Authorization", adminBearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("ARCHIVE"));
    }

    @Test
    void pendingProductCannotBeMarkedSold_returns400() throws Exception {
        Long id = submitAs(client, PRODUCT_NOM);

        mockMvc.perform(patch("/api/products/{id}/vendu", id)
                        .header("Authorization", bearer(client)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error")
                        .value("Le produit doit d'abord être validé et publié avant d'être marqué comme vendu"));
    }

    @Test
    void soldOrArchived_hiddenFromPublicCatalog_and404ForAnonymousDetail() throws Exception {
        Long soldId = publishAs(client, PRODUCT_NOM);
        Long archivedId = publishAs(otherClient, "Annonce archivee unique");

        mockMvc.perform(patch("/api/products/{id}/vendu", soldId)
                        .header("Authorization", bearer(client)))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/products/{id}/archiver", archivedId)
                        .header("Authorization", bearer(otherClient)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].nom", not(hasItem(PRODUCT_NOM))))
                .andExpect(jsonPath("$.content[*].nom", not(hasItem("Annonce archivee unique"))));

        mockMvc.perform(get("/api/products/{id}", soldId))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/products/{id}", archivedId))
                .andExpect(status().isNotFound());
    }

    @Test
    void listMine_includesSoldAndArchived() throws Exception {
        Long soldId = publishAs(client, PRODUCT_NOM);
        Long archivedId = publishAs(client, "Seconde annonce archivee");

        mockMvc.perform(patch("/api/products/{id}/vendu", soldId)
                        .header("Authorization", bearer(client)))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/products/{id}/archiver", archivedId)
                        .header("Authorization", bearer(client)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/products/me")
                        .header("Authorization", bearer(client)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[*].statut", hasItem("VENDU")))
                .andExpect(jsonPath("$.content[*].statut", hasItem("ARCHIVE")));
    }

    @Test
    void reactivateSoldAndArchived_returnsToPublicCatalog() throws Exception {
        Long soldId = publishAs(client, PRODUCT_NOM);
        Long archivedId = publishAs(client, "Annonce a reactiver");

        mockMvc.perform(patch("/api/products/{id}/vendu", soldId)
                        .header("Authorization", bearer(client)))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/products/{id}/archiver", archivedId)
                        .header("Authorization", bearer(client)))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/products/{id}/reactiver", soldId)
                        .header("Authorization", bearer(client)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("PUBLIE"));
        mockMvc.perform(patch("/api/products/{id}/reactiver", archivedId)
                        .header("Authorization", bearer(client)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("PUBLIE"));

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].nom", hasItem(PRODUCT_NOM)))
                .andExpect(jsonPath("$.content[*].nom", hasItem("Annonce a reactiver")));
    }

    @Test
    void listMine_returnsOnlyCurrentUserProducts() throws Exception {
        submitAs(client, PRODUCT_NOM);
        submitAs(otherClient, "Produit de l autre");

        mockMvc.perform(get("/api/products/me")
                        .header("Authorization", bearer(client)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].nom").value(PRODUCT_NOM))
                .andExpect(jsonPath("$.content[0].soumisParUserId").value(client.getId()));

        mockMvc.perform(get("/api/admin/products")
                        .param("statut", "EN_ATTENTE")
                        .header("Authorization", adminBearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].nom", hasItem(PRODUCT_NOM)))
                .andExpect(jsonPath("$.content[*].nom", hasItem("Produit de l autre")));
    }

    private Long publishAs(User user, String nom) throws Exception {
        Long id = submitAs(user, nom);
        mockMvc.perform(patch("/api/admin/products/{id}/valider", id)
                        .header("Authorization", adminBearer()))
                .andExpect(status().isOk());
        return id;
    }

    private Long submitAs(User user, String nom) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/products")
                        .header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitBody(nom, null)))
                .andExpect(status().isCreated())
                .andReturn();
        return jsonId(result);
    }

    private String submitBody(String nom, String statut) {
        String statutJson = statut == null ? "" : ",\"statut\":\"" + statut + "\"";
        return """
                {"nom":"%s","description":"Desc","prix":1500,"stock":2,"categoryId":%d%s}
                """.formatted(nom, category.getId(), statutJson);
    }

    private User saveClient(String nom, String telephone) {
        return userRepository.save(User.builder()
                .nom(nom)
                .telephone(telephone)
                .passwordHash(passwordEncoder.encode("secret12"))
                .telephoneVerifie(true)
                .role(Role.CLIENT)
                .build());
    }

    private void clean() {
        productRepository.deleteAll();
        notificationRepository.deleteAll();
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

    private Long jsonId(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private static MockMultipartFile jpeg() {
        return new MockMultipartFile("file", "photo.jpg", MediaType.IMAGE_JPEG_VALUE,
                new byte[]{(byte) 0xFF, (byte) 0xD8, 1, 2, 3});
    }
}
