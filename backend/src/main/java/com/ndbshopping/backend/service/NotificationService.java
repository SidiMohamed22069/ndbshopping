package com.ndbshopping.backend.service;

import com.ndbshopping.backend.dto.common.PageResponse;
import com.ndbshopping.backend.dto.notification.NotificationResponse;
import com.ndbshopping.backend.entity.Notification;
import com.ndbshopping.backend.entity.enums.NotificationType;
import com.ndbshopping.backend.exception.ApiException;
import com.ndbshopping.backend.repository.NotificationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Notifications internes admin : persistées en base + push WebSocket.
 * Aucun envoi WhatsApp / SMS n'est effectué ici.
 */
@Service
@Slf4j
public class NotificationService {

    public static final String ADMIN_TOPIC = "/topic/admin-notifications";

    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public NotificationService(
            NotificationRepository notificationRepository,
            SimpMessagingTemplate messagingTemplate
    ) {
        this.notificationRepository = notificationRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @Transactional
    public NotificationResponse createAndPush(NotificationType type, String message, String lienRessource) {
        Notification saved = notificationRepository.save(Notification.builder()
                .type(type)
                .message(message)
                .lienRessource(lienRessource)
                .lu(false)
                .build());
        NotificationResponse dto = NotificationResponse.from(saved);
        try {
            messagingTemplate.convertAndSend(ADMIN_TOPIC, dto);
        } catch (Exception ex) {
            log.warn("Push WebSocket notification échoué (la notification reste persistée)", ex);
        }
        return dto;
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(Boolean lu, Pageable pageable) {
        Page<Notification> page = lu == null
                ? notificationRepository.findAllByOrderByCreatedAtDesc(pageable)
                : notificationRepository.findByLuOrderByCreatedAtDesc(lu, pageable);
        return PageResponse.from(page.map(NotificationResponse::from));
    }

    @Transactional
    public NotificationResponse markAsRead(Long id) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Notification introuvable"));
        notification.setLu(true);
        return NotificationResponse.from(notification);
    }

    @Transactional(readOnly = true)
    public long countUnread() {
        return notificationRepository.countByLuFalse();
    }
}
