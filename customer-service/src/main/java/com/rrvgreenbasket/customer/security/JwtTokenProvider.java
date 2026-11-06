package com.rrvgreenbasket.customer.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * JwtTokenProvider
 *
 * <p><strong>What it does:</strong></p>
 * Defense-in-depth token validator and claims parser for Customer Service.
 * Validates HMAC-SHA256 signatures and extracts canonical claims (userId, email,
 * mobile, role, sid, status).
 */
@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    private final SecretKey signingKey;

    public JwtTokenProvider(@Value("${app.jwt.secret:404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970}") String secret) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, keyBytes.length);
            keyBytes = padded;
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractUserId(String token) {
        Claims claims = extractAllClaims(token);
        String subject = claims.getSubject();
        if (subject != null && !subject.isBlank()) {
            return subject;
        }
        String publicId = claims.get("public_id", String.class);
        if (publicId != null && !publicId.isBlank()) {
            return publicId;
        }
        return claims.get("userId", String.class);
    }

    public String extractEmail(String token) {
        Claims claims = extractAllClaims(token);
        String email = claims.get("email", String.class);
        if (email != null) {
            return email;
        }
        String mobile = claims.get("mobile_number", String.class);
        return mobile != null ? mobile : claims.getSubject();
    }

    public String extractRole(String token) {
        Claims claims = extractAllClaims(token);
        String role = claims.get("role", String.class);
        if ("ROLE_USER".equals(role)) {
            return "ROLE_CUSTOMER";
        }
        return role;
    }

    public String extractSessionId(String token) {
        Claims claims = extractAllClaims(token);
        return claims.get("sid", String.class);
    }

    public String extractStatus(String token) {
        Claims claims = extractAllClaims(token);
        return claims.get("status", String.class);
    }
}
