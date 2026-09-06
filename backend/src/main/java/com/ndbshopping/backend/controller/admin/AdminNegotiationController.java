package com.ndbshopping.backend.controller.admin;

import com.ndbshopping.backend.dto.common.PageResponse;
import com.ndbshopping.backend.dto.negotiation.NegotiationDecisionRequest;
import com.ndbshopping.backend.dto.negotiation.NegotiationMessageRequest;
import com.ndbshopping.backend.dto.negotiation.NegotiationOfferRequest;
import com.ndbshopping.backend.dto.negotiation.PriceNegotiationResponse;
import com.ndbshopping.backend.dto.notification.UnreadCountResponse;
import com.ndbshopping.backend.entity.enums.PriceNegotiationStatus;
import com.ndbshopping.backend.security.CurrentUserService;
import com.ndbshopping.backend.service.PriceNegotiationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/negotiations")
@Tag(name = "Admin — Négociations de prix")
public class AdminNegotiationController {

    private final PriceNegotiationService negotiationService;
    private final CurrentUserService currentUserService;

    public AdminNegotiationController(PriceNegotiationService negotiationService, CurrentUserService currentUserService) {
        this.negotiationService = negotiationService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    @Operation(summary = "Liste des négociations, filtrable par statut")
    public PageResponse<PriceNegotiationResponse> list(
            @RequestParam(required = false) PriceNegotiationStatus statut,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return negotiationService.adminList(statut, pageable);
    }

    @GetMapping("/count-en-attente")
    @Operation(summary = "Nombre de négociations en attente d'une réponse admin")
    public UnreadCountResponse countAwaitingAdmin() {
        return new UnreadCountResponse(negotiationService.countAwaitingAdmin());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'une négociation (fil de discussion inclus)")
    public PriceNegotiationResponse detail(@PathVariable Long id) {
        return negotiationService.getDetail(currentUserService.requireUser(), id);
    }

    @PostMapping("/{id}/messages")
    @Operation(summary = "Envoie un message dans le fil de discussion")
    public PriceNegotiationResponse addMessage(@PathVariable Long id, @Valid @RequestBody NegotiationMessageRequest request) {
        return negotiationService.addMessage(currentUserService.requireUser(), id, request.message());
    }

    @PostMapping("/{id}/offers")
    @Operation(summary = "Contre-propose un nouveau prix côté admin")
    public PriceNegotiationResponse counterOffer(@PathVariable Long id, @Valid @RequestBody NegotiationOfferRequest request) {
        return negotiationService.counterOffer(currentUserService.requireUser(), id, request.price(), request.message());
    }

    @PatchMapping("/{id}/accept")
    @Operation(summary = "Valide la proposition de l'acheteur")
    public PriceNegotiationResponse accept(@PathVariable Long id, @RequestBody(required = false) NegotiationDecisionRequest request) {
        String message = request == null ? null : request.message();
        return negotiationService.accept(currentUserService.requireUser(), id, message);
    }

    @PatchMapping("/{id}/reject")
    @Operation(summary = "Refuse la négociation")
    public PriceNegotiationResponse reject(@PathVariable Long id, @RequestBody(required = false) NegotiationDecisionRequest request) {
        String message = request == null ? null : request.message();
        return negotiationService.reject(currentUserService.requireUser(), id, message);
    }
}
