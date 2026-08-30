package com.ndbshopping.backend.dto.notification;

import com.ndbshopping.backend.entity.Notification;
import com.ndbshopping.backend.entity.enums.NotificationType;

import java.time.Instant;

public record NotificationResponse(
        Long id,
        NotificationType type,
        String message,
        String lienRessource,
        boolean lu,
        Instant createdAt
) {
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getMessage(),
                notification.getLienRessource(),
                notification.isLu(),
                notification.getCreatedAt()
        );
    }
}
