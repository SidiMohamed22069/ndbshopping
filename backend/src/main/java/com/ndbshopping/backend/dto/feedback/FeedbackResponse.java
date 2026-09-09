package com.ndbshopping.backend.dto.feedback;

import com.ndbshopping.backend.entity.UserFeedback;
import com.ndbshopping.backend.entity.enums.FeedbackCategory;
import com.ndbshopping.backend.entity.enums.FeedbackStatus;

import java.time.Instant;

public record FeedbackResponse(
        Long id,
        boolean anonymous,
        Long userId,
        String userNom,
        String contactInfo,
        FeedbackCategory category,
        String message,
        FeedbackStatus statut,
        Instant createdAt
) {
    public static FeedbackResponse from(UserFeedback feedback) {
        boolean anonymous = feedback.getUser() == null;
        return new FeedbackResponse(
                feedback.getId(),
                anonymous,
                anonymous ? null : feedback.getUser().getId(),
                anonymous ? null : feedback.getUser().getNom(),
                feedback.getContactInfo(),
                feedback.getCategory(),
                feedback.getMessage(),
                feedback.getStatut(),
                feedback.getCreatedAt()
        );
    }
}
