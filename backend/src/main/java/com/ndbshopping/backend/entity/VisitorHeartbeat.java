package com.ndbshopping.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Une ligne par visiteur (identifié par un cookie anonyme stable côté Django,
 * ou par "user:{id}" une fois connecté) : mise à jour à chaque battement plutôt
 * que ré-insérée, pour que COUNT(...) où lastSeenAt est récent donne directement
 * le nombre de visiteurs uniques "en ligne" / "sur les dernières 24h".
 */
@Entity
@Table(name = "visitor_heartbeats")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VisitorHeartbeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "visitor_key", nullable = false, unique = true, length = 190)
    private String visitorKey;

    @Column(nullable = false)
    private boolean authenticated;

    @Column(name = "first_seen_at", nullable = false, updatable = false)
    private Instant firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (firstSeenAt == null) {
            firstSeenAt = now;
        }
        if (lastSeenAt == null) {
            lastSeenAt = now;
        }
    }
}
