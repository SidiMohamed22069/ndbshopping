package com.ndbshopping.backend.controller;

import com.ndbshopping.backend.dto.feedback.CreateFeedbackRequest;
import com.ndbshopping.backend.dto.feedback.FeedbackResponse;
import com.ndbshopping.backend.security.CurrentUserService;
import com.ndbshopping.backend.service.UserFeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Boîte à idées / avis : 100% public, avec ou sans compte — voir SecurityConfig
 * (POST /api/feedbacks permitAll). Si un JWT valide est fourni, l'avis est
 * rattaché à l'utilisateur ; sinon il reste anonyme (voir UserFeedback.user).
 */
@RestController
@RequestMapping("/api/feedbacks")
@Tag(name = "Boîte à idées / Feedbacks")
public class FeedbackController {

    private final UserFeedbackService feedbackService;
    private final CurrentUserService currentUserService;

    public FeedbackController(UserFeedbackService feedbackService, CurrentUserService currentUserService) {
        this.feedbackService = feedbackService;
        this.currentUserService = currentUserService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Dépose un avis (visiteur anonyme ou connecté)")
    public FeedbackResponse create(@Valid @RequestBody CreateFeedbackRequest request, HttpServletRequest httpRequest) {
        return feedbackService.create(
                currentUserService.findUser().orElse(null),
                request.category(),
                request.message(),
                request.contactInfo(),
                clientIp(httpRequest)
        );
    }

    /**
     * Le navigateur n'appelle jamais l'API Spring directement (voir services/api_client.py
     * côté Django) : c'est le serveur Django qui relaie l'appel et transmet l'IP réelle
     * du visiteur via X-Forwarded-For. On retombe sur l'IP de la connexion TCP directe
     * (utile pour les tests, ou un appel API direct hors Django).
     */
    private static String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
