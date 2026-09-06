package com.ndbshopping.backend.service;

import com.ndbshopping.backend.dto.common.PageResponse;
import com.ndbshopping.backend.dto.negotiation.PriceNegotiationResponse;
import com.ndbshopping.backend.entity.PriceNegotiation;
import com.ndbshopping.backend.entity.PriceNegotiationMessage;
import com.ndbshopping.backend.entity.Product;
import com.ndbshopping.backend.entity.User;
import com.ndbshopping.backend.entity.enums.NegotiationActor;
import com.ndbshopping.backend.entity.enums.NotificationType;
import com.ndbshopping.backend.entity.enums.PriceNegotiationStatus;
import com.ndbshopping.backend.entity.enums.ProductStatus;
import com.ndbshopping.backend.entity.enums.Role;
import com.ndbshopping.backend.exception.ApiException;
import com.ndbshopping.backend.repository.PriceNegotiationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Négociation de prix produit <-> admin, avec fil de discussion.
 * Un seul thread "ouvert" (PENDING/COUNTER_OFFER) par couple (produit, acheteur).
 */
@Service
public class PriceNegotiationService {

    private static final List<PriceNegotiationStatus> OPEN_STATUSES =
            List.of(PriceNegotiationStatus.PENDING, PriceNegotiationStatus.COUNTER_OFFER);

    private final PriceNegotiationRepository negotiationRepository;
    private final ProductService productService;
    private final NotificationService notificationService;

    public PriceNegotiationService(
            PriceNegotiationRepository negotiationRepository,
            ProductService productService,
            NotificationService notificationService
    ) {
        this.negotiationRepository = negotiationRepository;
        this.productService = productService;
        this.notificationService = notificationService;
    }

    @Transactional
    public PriceNegotiationResponse start(User buyer, Long productId, BigDecimal proposedPrice, String message) {
        Product product = productService.get(productId);
        if (product.getStatut() != ProductStatus.PUBLIE) {
            throw ApiException.badRequest("Ce produit n'est pas disponible à la négociation");
        }

        PriceNegotiation negotiation = negotiationRepository
                .findFirstByProductIdAndUserIdAndStatutInOrderByCreatedAtDesc(productId, buyer.getId(), OPEN_STATUSES)
                .orElseGet(() -> PriceNegotiation.builder()
                        .product(product)
                        .user(buyer)
                        .prixInitial(product.getPrix())
                        .proposedPrice(proposedPrice)
                        .statut(PriceNegotiationStatus.PENDING)
                        .derniereActionPar(NegotiationActor.USER)
                        .build());
        negotiation.setProposedPrice(proposedPrice);
        negotiation.setStatut(PriceNegotiationStatus.PENDING);
        negotiation.setDerniereActionPar(NegotiationActor.USER);
        PriceNegotiation saved = negotiationRepository.save(negotiation);

        addMessageInternal(saved, buyer, offerText(message, proposedPrice), proposedPrice);

        notificationService.createAndPush(
                NotificationType.NEGOCIATION_PRIX,
                "Négociation — " + product.getNom() + " — " + buyer.getNom() + " propose " + proposedPrice + " UM",
                "/negotiations/" + saved.getId()
        );
        return PriceNegotiationResponse.from(saved);
    }

    @Transactional
    public PriceNegotiationResponse counterOffer(User actor, Long id, BigDecimal price, String message) {
        PriceNegotiation negotiation = get(id);
        assertParticipant(negotiation, actor);
        assertOpen(negotiation);

        NegotiationActor actorRole = actorRole(actor);
        negotiation.setProposedPrice(price);
        negotiation.setStatut(PriceNegotiationStatus.COUNTER_OFFER);
        negotiation.setDerniereActionPar(actorRole);
        addMessageInternal(negotiation, actor, offerText(message, price), price);

        if (actorRole == NegotiationActor.ADMIN) {
            notificationService.createForUser(
                    negotiation.getUser(),
                    NotificationType.NEGOCIATION_REPONSE,
                    "Contre-offre reçue pour " + negotiation.getProduct().getNom() + " : " + price + " UM",
                    "/negotiations/" + negotiation.getId()
            );
        } else {
            notificationService.createAndPush(
                    NotificationType.NEGOCIATION_PRIX,
                    "Contre-offre — " + negotiation.getProduct().getNom() + " — "
                            + actor.getNom() + " propose " + price + " UM",
                    "/negotiations/" + negotiation.getId()
            );
        }
        return PriceNegotiationResponse.from(negotiation);
    }

    @Transactional
    public PriceNegotiationResponse accept(User actor, Long id, String message) {
        PriceNegotiation negotiation = get(id);
        assertParticipant(negotiation, actor);
        assertOpen(negotiation);

        NegotiationActor actorRole = actorRole(actor);
        if (negotiation.getDerniereActionPar() == actorRole) {
            throw ApiException.badRequest("Vous ne pouvez pas accepter votre propre proposition");
        }
        negotiation.setStatut(PriceNegotiationStatus.ACCEPTED);
        addMessageInternal(
                negotiation,
                actor,
                blankToDefault(message, "Prix accepté : " + negotiation.getProposedPrice() + " UM"),
                null
        );
        if (actorRole == NegotiationActor.ADMIN) {
            notificationService.createForUser(
                    negotiation.getUser(),
                    NotificationType.NEGOCIATION_REPONSE,
                    "Votre proposition pour " + negotiation.getProduct().getNom() + " a été acceptée à "
                            + negotiation.getProposedPrice() + " UM",
                    "/negotiations/" + negotiation.getId()
            );
        }
        return PriceNegotiationResponse.from(negotiation);
    }

    @Transactional
    public PriceNegotiationResponse reject(User actor, Long id, String message) {
        PriceNegotiation negotiation = get(id);
        assertParticipant(negotiation, actor);
        assertOpen(negotiation);

        negotiation.setStatut(PriceNegotiationStatus.REJECTED);
        addMessageInternal(negotiation, actor, blankToDefault(message, "Négociation refusée"), null);
        if (isAdmin(actor)) {
            notificationService.createForUser(
                    negotiation.getUser(),
                    NotificationType.NEGOCIATION_REPONSE,
                    "Votre proposition pour " + negotiation.getProduct().getNom() + " a été refusée",
                    "/negotiations/" + negotiation.getId()
            );
        }
        return PriceNegotiationResponse.from(negotiation);
    }

    @Transactional
    public PriceNegotiationResponse addMessage(User actor, Long id, String text) {
        PriceNegotiation negotiation = get(id);
        assertParticipant(negotiation, actor);
        addMessageInternal(negotiation, actor, text, null);
        return PriceNegotiationResponse.from(negotiation);
    }

    @Transactional(readOnly = true)
    public PriceNegotiationResponse getDetail(User actor, Long id) {
        PriceNegotiation negotiation = get(id);
        assertParticipant(negotiation, actor);
        return PriceNegotiationResponse.from(negotiation);
    }

    @Transactional(readOnly = true)
    public PriceNegotiationResponse activeForProduct(User buyer, Long productId) {
        return negotiationRepository
                .findFirstByProductIdAndUserIdAndStatutInOrderByCreatedAtDesc(
                        productId, buyer.getId(),
                        List.of(PriceNegotiationStatus.PENDING, PriceNegotiationStatus.COUNTER_OFFER, PriceNegotiationStatus.ACCEPTED))
                .map(PriceNegotiationResponse::from)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public PageResponse<PriceNegotiationResponse> listMine(User buyer, Pageable pageable) {
        Page<PriceNegotiation> page = negotiationRepository.findByUserIdOrderByUpdatedAtDesc(buyer.getId(), pageable);
        return PageResponse.from(page.map(PriceNegotiationResponse::summary));
    }

    @Transactional(readOnly = true)
    public PageResponse<PriceNegotiationResponse> adminList(PriceNegotiationStatus statut, Pageable pageable) {
        Page<PriceNegotiation> page = statut == null
                ? negotiationRepository.findAllByOrderByUpdatedAtDesc(pageable)
                : negotiationRepository.findByStatutOrderByUpdatedAtDesc(statut, pageable);
        return PageResponse.from(page.map(PriceNegotiationResponse::summary));
    }

    @Transactional(readOnly = true)
    public long countAwaitingAdmin() {
        return negotiationRepository.countByStatutInAndDerniereActionPar(OPEN_STATUSES, NegotiationActor.USER);
    }

    /** Négociation ACCEPTÉE appartenant à cet utilisateur pour ce produit — utilisé par le panier. */
    @Transactional(readOnly = true)
    public Optional<PriceNegotiation> findAcceptedForCart(Long negotiationId, Long userId, Long productId) {
        return negotiationRepository.findById(negotiationId)
                .filter(n -> n.getStatut() == PriceNegotiationStatus.ACCEPTED)
                .filter(n -> n.getUser().getId().equals(userId))
                .filter(n -> n.getProduct().getId().equals(productId));
    }

    PriceNegotiation get(Long id) {
        return negotiationRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Négociation introuvable"));
    }

    private void addMessageInternal(PriceNegotiation negotiation, User sender, String text, BigDecimal offeredPrice) {
        negotiation.getMessages().add(PriceNegotiationMessage.builder()
                .negotiation(negotiation)
                .sender(sender)
                .message(text)
                .offeredPrice(offeredPrice)
                .build());
    }

    private static String offerText(String message, BigDecimal price) {
        String base = "Proposition : " + price + " UM";
        return (message == null || message.isBlank()) ? base : base + " — " + message.trim();
    }

    private static String blankToDefault(String message, String fallback) {
        return (message == null || message.isBlank()) ? fallback : message.trim();
    }

    private static boolean isAdmin(User actor) {
        return actor.getRole() == Role.ADMIN;
    }

    private static NegotiationActor actorRole(User actor) {
        return isAdmin(actor) ? NegotiationActor.ADMIN : NegotiationActor.USER;
    }

    private static void assertParticipant(PriceNegotiation negotiation, User actor) {
        boolean owner = negotiation.getUser().getId().equals(actor.getId());
        if (!owner && !isAdmin(actor)) {
            throw ApiException.forbidden("Accès refusé");
        }
    }

    private static void assertOpen(PriceNegotiation negotiation) {
        if (negotiation.getStatut() != PriceNegotiationStatus.PENDING
                && negotiation.getStatut() != PriceNegotiationStatus.COUNTER_OFFER) {
            throw ApiException.badRequest("Cette négociation est déjà clôturée");
        }
    }
}
