package com.ndbshopping.backend.dto.analytics;

public record AnalyticsStatsResponse(
        long usersOnlineAuth,
        long usersOnlineGuest,
        long visits24h
) {
}
