package com.rrvgreenbasket.gateway.util;

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
import java.util.Date;

/**
 * JwtUtil
 *
 * <p><strong>What it does:</strong></p>
 * Performs cryptographic validation, signature verification, and canonical claims extraction
 * for inbound JWT tokens at the API Gateway edge.
 */
@Component
public class JwtUtil {

    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);

    @Value("${app.jwt.secret:404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970}")
    private String jwtSecret;

    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, keyBytes.length);
            keyBytes = padded;
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public boolean validateToken(String token) {
        try {
            Claims claims = extractAllClaims(token);
            boolean expired = claims.getExpiration().before(new Date());
            if (expired) {
                log.warn("JWT validation failed: Token is expired");
                return false;
            }
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("JWT validation failed: {}", e.getMessage());
            return false;
        }
    }

    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractUserId(String token) {
        Claims claims = extractAllClaims(token);
        String publicId = claims.get("public_id", String.class);
        if (publicId != null && !publicId.isBlank()) {
            return publicId;
        }
        String userId = claims.get("userId", String.class);
        if (userId != null && !userId.isBlank()) {
            return userId;
        }
        return claims.getSubject();
    }

    public String extractEmail(String token) {
        Claims claims = extractAllClaims(token);
        return claims.get("email", String.class);
    }

    public String extractMobileNumber(String token) {
        Claims claims = extractAllClaims(token);
        return claims.get("mobile_number", String.class);
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
