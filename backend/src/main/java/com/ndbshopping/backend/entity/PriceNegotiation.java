package com.ndbshopping.backend.entity;

import com.ndbshopping.backend.entity.enums.NegotiationActor;
import com.ndbshopping.backend.entity.enums.PriceNegotiationStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "price_negotiations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PriceNegotiation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** L'acheteur qui négocie. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Prix catalogue au moment de l'ouverture de la négociation (référence). */
    @Column(name = "prix_initial", nullable = false, precision = 14, scale = 2)
    private BigDecimal prixInitial;

    /** Prix actuellement sur la table (dernière offre, quel que soit l'auteur). */
    @Column(name = "proposed_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal proposedPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PriceNegotiationStatus statut = PriceNegotiationStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "derniere_action_par", nullable = false, length = 10)
    @Builder.Default
    private NegotiationActor derniereActionPar = NegotiationActor.USER;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "negotiation", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC, id ASC")
    @Builder.Default
    private List<PriceNegotiationMessage> messages = new ArrayList<>();

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
