package com.ndbshopping.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String frontendUrl,
        Upload upload,
        Jwt jwt,
        Otp otp,
        Chinguisoft chinguisoft
) {
    public record Upload(String dir) {
    }

    public record Jwt(String secret, long expirationMs) {
    }

    public record Otp(
            int ttlMinutes,
            int maxSendPerWindow,
            int sendWindowMinutes,
            int maxVerifyAttempts
    ) {
    }

    public record Chinguisoft(
            String baseUrl,
            String validationKey,
            String token,
            int lowBalanceThreshold,
            boolean mockEnabled,
            String mockCode
    ) {
    }
}
