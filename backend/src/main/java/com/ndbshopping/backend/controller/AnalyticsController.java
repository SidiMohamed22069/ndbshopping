package com.ndbshopping.backend.controller;

import com.ndbshopping.backend.dto.analytics.AnalyticsStatsResponse;
import com.ndbshopping.backend.dto.analytics.HeartbeatRequest;
import com.ndbshopping.backend.service.VisitorAnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Fréquentation du site (barre de stats du header). Public : doit fonctionner
 * pour les visiteurs anonymes comme pour les comptes connectés, sans JWT.
 */
@RestController
@RequestMapping("/api/analytics")
@Tag(name = "Analytics — Fréquentation")
public class AnalyticsController {

    private final VisitorAnalyticsService visitorAnalyticsService;

    public AnalyticsController(VisitorAnalyticsService visitorAnalyticsService) {
        this.visitorAnalyticsService = visitorAnalyticsService;
    }

    @PostMapping("/heartbeat")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Signale qu'un visiteur (anonyme ou connecté) est actif")
    public void heartbeat(@Valid @RequestBody HeartbeatRequest request) {
        visitorAnalyticsService.recordHeartbeat(request.visitorKey(), request.authenticated());
    }

    @GetMapping("/stats")
    @Operation(summary = "Compteurs de fréquentation courants (connectés, anonymes, visites 24h)")
    public AnalyticsStatsResponse stats() {
        return visitorAnalyticsService.currentStats();
    }
}
