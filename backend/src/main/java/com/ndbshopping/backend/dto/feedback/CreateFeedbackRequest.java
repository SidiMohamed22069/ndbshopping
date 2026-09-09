package com.ndbshopping.backend.dto.feedback;

import com.ndbshopping.backend.entity.enums.FeedbackCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateFeedbackRequest(
        @NotNull FeedbackCategory category,
        @NotBlank @Size(max = 4000) String message,
        @Size(max = 190) String contactInfo
) {
}
