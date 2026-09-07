package com.ndbshopping.backend.service;

import com.ndbshopping.backend.dto.analytics.AnalyticsStatsResponse;
import com.ndbshopping.backend.entity.VisitorHeartbeat;
import com.ndbshopping.backend.repository.VisitorHeartbeatRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Fréquentation en temps quasi réel : chaque visiteur (cookie anonyme stable
 * ou "user:{id}" une fois connecté) met à jour une seule ligne à chaque
 * battement envoyé par le middleware Django. "En ligne" = vu dans la fenêtre
 * {@link #ONLINE_WINDOW}, "visites 24h" = vu dans les dernières 24h.
 */
@Service
public class VisitorAnalyticsService {

    static final Duration ONLINE_WINDOW = Duration.ofMinutes(5);
    static final Duration DAY_WINDOW = Duration.ofHours(24);
    private static final Duration RETENTION = Duration.ofDays(30);
    private static final int MAX_KEY_LENGTH = 190;

    private final VisitorHeartbeatRepository repository;

    public VisitorAnalyticsService(VisitorHeartbeatRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void recordHeartbeat(String visitorKey, boolean authenticated) {
        String key = normalizeKey(visitorKey);
        Instant now = Instant.now();
        VisitorHeartbeat heartbeat = repository.findByVisitorKey(key).orElseGet(() -> VisitorHeartbeat.builder()
                .visitorKey(key)
                .firstSeenAt(now)
                .build());
        heartbeat.setAuthenticated(authenticated);
        heartbeat.setLastSeenAt(now);
        repository.save(heartbeat);

        // Purge opportuniste (pas de scheduler dédié pour une fonctionnalité aussi légère) :
        // ~1 battement sur 200 déclenche le nettoyage des lignes inactives depuis 30 jours.
        if (ThreadLocalRandom.current().nextInt(200) == 0) {
            repository.deleteByLastSeenAtBefore(now.minus(RETENTION));
        }
    }

    @Transactional(readOnly = true)
    public AnalyticsStatsResponse currentStats() {
        Instant now = Instant.now();
        Instant onlineSince = now.minus(ONLINE_WINDOW);
        Instant daySince = now.minus(DAY_WINDOW);
        return new AnalyticsStatsResponse(
                repository.countByAuthenticatedTrueAndLastSeenAtAfter(onlineSince),
                repository.countByAuthenticatedFalseAndLastSeenAtAfter(onlineSince),
                repository.countByLastSeenAtAfter(daySince)
        );
    }

    private static String normalizeKey(String visitorKey) {
        String trimmed = visitorKey.trim();
        return trimmed.length() > MAX_KEY_LENGTH ? trimmed.substring(0, MAX_KEY_LENGTH) : trimmed;
    }
}
