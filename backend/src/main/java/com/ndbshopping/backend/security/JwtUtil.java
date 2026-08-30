package com.ndbshopping.backend.security;

import com.ndbshopping.backend.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expirationMs;

    public JwtUtil(AppProperties appProperties) {
        String secret = appProperties.jwt().secret();
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("JWT_SECRET doit faire au moins 32 caractères");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = appProperties.jwt().expirationMs();
    }

    public String generateToken(Long userId, String telephone, String role) {
        Date now = new Date();
        return Jwts.builder()
                .subject(telephone)
                .claim("userId", userId)
                .claim("role", role)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(key)
                .compact();
    }

    public boolean isValid(String token) {
        try {
            parse(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    public String extractTelephone(String token) {
        return parse(token).getSubject();
    }

    public Long extractUserId(String token) {
        Number userId = parse(token).get("userId", Number.class);
        return userId == null ? null : userId.longValue();
    }

    private Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
