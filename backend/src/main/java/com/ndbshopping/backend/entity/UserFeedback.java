package com.ndbshopping.backend.entity;

import com.ndbshopping.backend.entity.enums.FeedbackCategory;
import com.ndbshopping.backend.entity.enums.FeedbackStatus;
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Avis / boîte à idées : accessible à 100% des visiteurs, avec ou sans compte
 * (voir FeedbackController — endpoint public). {@link #user} reste null pour
 * un visiteur anonyme.
 */
@Entity
@Table(name = "user_feedbacks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "user_id", nullable = true)
    private User user;

    @Column(name = "contact_info", length = 190)
    private String contactInfo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FeedbackCategory category;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private FeedbackStatus statut = FeedbackStatus.NOUVEAU;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (statut == null) {
            statut = FeedbackStatus.NOUVEAU;
        }
    }
}
