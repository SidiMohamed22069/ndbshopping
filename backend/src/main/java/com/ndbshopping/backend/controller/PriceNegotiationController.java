package com.ndbshopping.backend.controller;

import com.ndbshopping.backend.dto.common.PageResponse;
import com.ndbshopping.backend.dto.negotiation.NegotiationDecisionRequest;
import com.ndbshopping.backend.dto.negotiation.NegotiationMessageRequest;
import com.ndbshopping.backend.dto.negotiation.NegotiationOfferRequest;
import com.ndbshopping.backend.dto.negotiation.PriceNegotiationResponse;
import com.ndbshopping.backend.dto.negotiation.StartNegotiationRequest;
import com.ndbshopping.backend.security.CurrentUserService;
import com.ndbshopping.backend.service.PriceNegotiationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/negotiations")
@Tag(name = "Négociation de prix (client)")
public class PriceNegotiationController {

    private final PriceNegotiationService negotiationService;
    private final CurrentUserService currentUserService;

    public PriceNegotiationController(PriceNegotiationService negotiationService, CurrentUserService currentUserService) {
        this.negotiationService = negotiationService;
        this.currentUserService = currentUserService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Propose un prix pour un produit (ouvre ou met à jour la négociation en cours)")
    public PriceNegotiationResponse start(@Valid @RequestBody StartNegotiationRequest request) {
        return negotiationService.start(
                currentUserService.requireUser(), request.productId(), request.proposedPrice(), request.message());
    }

    @GetMapping("/me")
    @Operation(summary = "Mes négociations")
    public PageResponse<PriceNegotiationResponse> mine(@PageableDefault(size = 20) Pageable pageable) {
        return negotiationService.listMine(currentUserService.requireUser(), pageable);
    }

    @GetMapping("/product/{productId}")
    @Operation(summary = "Ma négociation en cours (ou acceptée) pour ce produit, s'il y en a une")
    public ResponseEntity<PriceNegotiationResponse> activeForProduct(@PathVariable Long productId) {
        PriceNegotiationResponse response = negotiationService.activeForProduct(currentUserService.requireUser(), productId);
        return response == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(response);
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
    @Operation(summary = "Contre-propose un nouveau prix")
    public PriceNegotiationResponse counterOffer(@PathVariable Long id, @Valid @RequestBody NegotiationOfferRequest request) {
        return negotiationService.counterOffer(currentUserService.requireUser(), id, request.price(), request.message());
    }

    @PatchMapping("/{id}/accept")
    @Operation(summary = "Accepte la dernière offre de l'autre partie")
    public PriceNegotiationResponse accept(@PathVariable Long id, @RequestBody(required = false) NegotiationDecisionRequest request) {
        String message = request == null ? null : request.message();
        return negotiationService.accept(currentUserService.requireUser(), id, message);
    }

    @PatchMapping("/{id}/reject")
    @Operation(summary = "Refuse / clôture la négociation")
    public PriceNegotiationResponse reject(@PathVariable Long id, @RequestBody(required = false) NegotiationDecisionRequest request) {
        String message = request == null ? null : request.message();
        return negotiationService.reject(currentUserService.requireUser(), id, message);
    }
}
