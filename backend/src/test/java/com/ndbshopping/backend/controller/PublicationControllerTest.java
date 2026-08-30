package com.ndbshopping.backend.controller;

import com.ndbshopping.backend.entity.Publication;
import com.ndbshopping.backend.entity.enums.PublicationStatus;
import com.ndbshopping.backend.repository.PublicationRepository;
import com.ndbshopping.backend.service.OtpService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PublicationControllerTest {

    @MockitoBean
    private RedisConnectionFactory redisConnectionFactory;

    @MockitoBean
    private OtpService otpService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PublicationRepository publicationRepository;

    @Test
    void misesEnAvant_returnsOnlyPublishedFeatured() throws Exception {
        publicationRepository.save(featured("Bandeau publié", PublicationStatus.PUBLIE, true));
        publicationRepository.save(featured("Actualité classique", PublicationStatus.PUBLIE, false));
        publicationRepository.save(featured("Bandeau brouillon", PublicationStatus.BROUILLON, true));

        mockMvc.perform(get("/api/publications/mises-en-avant"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[*].titre", hasItem("Bandeau publié")))
                .andExpect(jsonPath("$[*].titre", not(hasItem("Actualité classique"))))
                .andExpect(jsonPath("$[*].titre", not(hasItem("Bandeau brouillon"))))
                .andExpect(jsonPath("$[?(@.titre=='Bandeau publié')].misEnAvant").value(hasItem(true)))
                .andExpect(jsonPath("$[?(@.titre=='Bandeau publié')].statut").value(hasItem("PUBLIE")));
    }

    @Test
    void listPublic_stillReturnsAllPublishedNews() throws Exception {
        publicationRepository.save(featured("News milieu", PublicationStatus.PUBLIE, false));
        publicationRepository.save(featured("News bandeau", PublicationStatus.PUBLIE, true));
        publicationRepository.save(featured("News brouillon", PublicationStatus.BROUILLON, false));

        mockMvc.perform(get("/api/publications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].titre", hasItem("News milieu")))
                .andExpect(jsonPath("$.content[*].titre", hasItem("News bandeau")))
                .andExpect(jsonPath("$.content[*].titre", not(hasItem("News brouillon"))));
    }

    private static Publication featured(String titre, PublicationStatus statut, boolean misEnAvant) {
        return Publication.builder()
                .titre(titre)
                .contenu("Contenu " + titre)
                .statut(statut)
                .misEnAvant(misEnAvant)
                .build();
    }
}
