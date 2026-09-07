package com.ndbshopping.backend.service;

import com.ndbshopping.backend.dto.analytics.AnalyticsStatsResponse;
import com.ndbshopping.backend.entity.VisitorHeartbeat;
import com.ndbshopping.backend.repository.VisitorHeartbeatRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VisitorAnalyticsServiceTest {

    @Mock
    private VisitorHeartbeatRepository repository;

    @Test
    void recordHeartbeat_createsNewRowWhenVisitorUnknown() {
        VisitorAnalyticsService service = new VisitorAnalyticsService(repository);
        when(repository.findByVisitorKey("anon:abc")).thenReturn(Optional.empty());

        service.recordHeartbeat("anon:abc", false);

        ArgumentCaptor<VisitorHeartbeat> captor = ArgumentCaptor.forClass(VisitorHeartbeat.class);
        verify(repository).save(captor.capture());
        VisitorHeartbeat saved = captor.getValue();
        assertEquals("anon:abc", saved.getVisitorKey());
        assertTrue(!saved.isAuthenticated());
        assertNotNull(saved.getLastSeenAt());
    }

    @Test
    void recordHeartbeat_updatesExistingRowInPlace() {
        VisitorAnalyticsService service = new VisitorAnalyticsService(repository);
        Instant firstSeen = Instant.now().minusSeconds(3600);
        VisitorHeartbeat existing = VisitorHeartbeat.builder()
                .id(1L)
                .visitorKey("user:42")
                .authenticated(false)
                .firstSeenAt(firstSeen)
                .lastSeenAt(firstSeen)
                .build();
        when(repository.findByVisitorKey("user:42")).thenReturn(Optional.of(existing));

        service.recordHeartbeat("user:42", true);

        ArgumentCaptor<VisitorHeartbeat> captor = ArgumentCaptor.forClass(VisitorHeartbeat.class);
        verify(repository).save(captor.capture());
        VisitorHeartbeat saved = captor.getValue();
        assertEquals(1L, saved.getId());
        assertEquals(firstSeen, saved.getFirstSeenAt());
        assertTrue(saved.isAuthenticated());
        assertTrue(saved.getLastSeenAt().isAfter(firstSeen));
    }

    @Test
    void currentStats_queriesOnlineAndDayWindows() {
        VisitorAnalyticsService service = new VisitorAnalyticsService(repository);
        when(repository.countByAuthenticatedTrueAndLastSeenAtAfter(any())).thenReturn(3L);
        when(repository.countByAuthenticatedFalseAndLastSeenAtAfter(any())).thenReturn(7L);
        when(repository.countByLastSeenAtAfter(any())).thenReturn(120L);

        AnalyticsStatsResponse stats = service.currentStats();

        assertEquals(3L, stats.usersOnlineAuth());
        assertEquals(7L, stats.usersOnlineGuest());
        assertEquals(120L, stats.visits24h());

        ArgumentCaptor<Instant> onlineCutoff = ArgumentCaptor.forClass(Instant.class);
        verify(repository, times(1)).countByAuthenticatedTrueAndLastSeenAtAfter(onlineCutoff.capture());
        assertTrue(onlineCutoff.getValue().isBefore(Instant.now()));
        assertTrue(onlineCutoff.getValue().isAfter(Instant.now().minus(VisitorAnalyticsService.ONLINE_WINDOW).minusSeconds(5)));

        verify(repository, times(0)).deleteByLastSeenAtBefore(eq(Instant.now()));
    }
}
