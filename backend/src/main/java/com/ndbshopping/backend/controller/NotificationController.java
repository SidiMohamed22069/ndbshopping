package com.ndbshopping.backend.controller;

import com.ndbshopping.backend.dto.common.PageResponse;
import com.ndbshopping.backend.dto.notification.NotificationResponse;
import com.ndbshopping.backend.dto.notification.UnreadCountResponse;
import com.ndbshopping.backend.security.CurrentUserService;
import com.ndbshopping.backend.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Notifications privées d'un client : négociations de prix, statut de commande.
 * 100% in-app — aucun SMS/e-mail n'est déclenché ici.
 */
@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notifications (client)")
public class NotificationController {

    private final NotificationService notificationService;
    private final CurrentUserService currentUserService;

    public NotificationController(NotificationService notificationService, CurrentUserService currentUserService) {
        this.notificationService = notificationService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/me")
    @Operation(summary = "Mes notifications (plus récentes d'abord)")
    public PageResponse<NotificationResponse> mine(@PageableDefault(size = 20) Pageable pageable) {
        return notificationService.listMine(currentUserService.requireUser(), pageable);
    }

    @GetMapping("/count-non-lues")
    @Operation(summary = "Nombre de mes notifications non lues (badge cloche)")
    public UnreadCountResponse unreadCount() {
        return new UnreadCountResponse(notificationService.countUnreadMine(currentUserService.requireUser()));
    }

    @PatchMapping("/{id}/lire")
    @Operation(summary = "Marque une de mes notifications comme lue")
    public NotificationResponse markAsRead(@PathVariable Long id) {
        return notificationService.markMineAsRead(currentUserService.requireUser(), id);
    }

    @PatchMapping("/lire-tout")
    @Operation(summary = "Marque toutes mes notifications comme lues")
    public void markAllAsRead() {
        notificationService.markAllMineAsRead(currentUserService.requireUser());
    }
}
