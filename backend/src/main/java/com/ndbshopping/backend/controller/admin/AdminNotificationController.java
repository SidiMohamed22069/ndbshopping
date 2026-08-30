package com.ndbshopping.backend.controller.admin;

import com.ndbshopping.backend.dto.common.PageResponse;
import com.ndbshopping.backend.dto.notification.NotificationResponse;
import com.ndbshopping.backend.dto.notification.UnreadCountResponse;
import com.ndbshopping.backend.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/notifications")
@Tag(name = "Admin — Notifications")
public class AdminNotificationController {

    private final NotificationService notificationService;

    public AdminNotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    @Operation(summary = "Liste paginée des notifications (plus récentes d'abord)")
    public PageResponse<NotificationResponse> list(
            @RequestParam(required = false) Boolean lu,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return notificationService.list(lu, pageable);
    }

    @PatchMapping("/{id}/lire")
    @Operation(summary = "Marque une notification comme lue")
    public NotificationResponse markAsRead(@PathVariable Long id) {
        return notificationService.markAsRead(id);
    }

    @GetMapping("/count-non-lues")
    @Operation(summary = "Nombre de notifications non lues (badge)")
    public UnreadCountResponse unreadCount() {
        return new UnreadCountResponse(notificationService.countUnread());
    }
}
