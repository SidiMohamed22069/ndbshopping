package com.ndbshopping.backend.service;

import com.ndbshopping.backend.dto.common.PageResponse;
import com.ndbshopping.backend.dto.feedback.FeedbackResponse;
import com.ndbshopping.backend.entity.User;
import com.ndbshopping.backend.entity.UserFeedback;
import com.ndbshopping.backend.entity.enums.FeedbackCategory;
import com.ndbshopping.backend.entity.enums.FeedbackStatus;
import com.ndbshopping.backend.exception.ApiException;
import com.ndbshopping.backend.repository.UserFeedbackRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Boîte à idées : 100% des visiteurs peuvent déposer un avis, connectés ou
 * non (voir FeedbackController — endpoint public, sans JWT requis).
 */
@Service
public class UserFeedbackService {

    private static final Duration SPAM_WINDOW = Duration.ofMinutes(10);
    private static final long SPAM_MAX_PER_WINDOW = 5;

    private final UserFeedbackRepository feedbackRepository;

    public UserFeedbackService(UserFeedbackRepository feedbackRepository) {
        this.feedbackRepository = feedbackRepository;
    }

    @Transactional
    public FeedbackResponse create(User user, FeedbackCategory category, String message, String contactInfo, String ipAddress) {
        if (ipAddress != null && !ipAddress.isBlank()) {
            long recent = feedbackRepository.countByIpAddressAndCreatedAtAfter(ipAddress, Instant.now().minus(SPAM_WINDOW));
            if (recent >= SPAM_MAX_PER_WINDOW) {
                throw ApiException.tooManyRequests("Trop d'avis envoyés récemment. Réessayez plus tard.");
            }
        }
        UserFeedback feedback = UserFeedback.builder()
                .user(user)
                .category(category)
                .message(message.trim())
                .contactInfo(blankToNull(contactInfo))
                .ipAddress(blankToNull(ipAddress))
                .statut(FeedbackStatus.NOUVEAU)
                .build();
        return FeedbackResponse.from(feedbackRepository.save(feedback));
    }

    @Transactional(readOnly = true)
    public PageResponse<FeedbackResponse> adminSearch(FeedbackCategory category, FeedbackStatus statut, Pageable pageable) {
        Page<UserFeedback> page = feedbackRepository.search(category, statut, pageable);
        return PageResponse.from(page.map(FeedbackResponse::from));
    }

    @Transactional
    public FeedbackResponse updateStatus(Long id, FeedbackStatus statut) {
        UserFeedback feedback = feedbackRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Avis introuvable"));
        feedback.setStatut(statut);
        return FeedbackResponse.from(feedback);
    }

    @Transactional(readOnly = true)
    public long countNouveaux() {
        return feedbackRepository.countByStatut(FeedbackStatus.NOUVEAU);
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
