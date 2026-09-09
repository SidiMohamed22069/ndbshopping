package com.ndbshopping.backend.dto.feedback;

import com.ndbshopping.backend.entity.enums.FeedbackStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateFeedbackStatusRequest(@NotNull FeedbackStatus statut) {
}
