package com.ndbshopping.backend.controller.admin;

import com.ndbshopping.backend.dto.common.PageResponse;
import com.ndbshopping.backend.dto.feedback.FeedbackResponse;
import com.ndbshopping.backend.dto.feedback.UpdateFeedbackStatusRequest;
import com.ndbshopping.backend.dto.notification.UnreadCountResponse;
import com.ndbshopping.backend.entity.enums.FeedbackCategory;
import com.ndbshopping.backend.entity.enums.FeedbackStatus;
import com.ndbshopping.backend.service.UserFeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/feedbacks")
@Tag(name = "Admin — Boîte à idées / Feedbacks")
public class AdminFeedbackController {

    private final UserFeedbackService feedbackService;

    public AdminFeedbackController(UserFeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @GetMapping
    @Operation(summary = "Liste des avis, filtrable par catégorie et statut")
    public PageResponse<FeedbackResponse> list(
            @RequestParam(required = false) FeedbackCategory category,
            @RequestParam(required = false) FeedbackStatus statut,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return feedbackService.adminSearch(category, statut, pageable);
    }

    @PatchMapping("/{id}/statut")
    @Operation(summary = "Change le statut d'un avis (Lu / Traité)")
    public FeedbackResponse updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateFeedbackStatusRequest request
    ) {
        return feedbackService.updateStatus(id, request.statut());
    }

    @GetMapping("/count-nouveaux")
    @Operation(summary = "Nombre d'avis non encore traités (badge)")
    public UnreadCountResponse newCount() {
        return new UnreadCountResponse(feedbackService.countNouveaux());
    }
}
