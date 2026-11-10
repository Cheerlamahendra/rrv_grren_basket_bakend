package com.rrvgreenbasket.review.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class JwtTokenProvider {

    private final SecretKey key;

    public JwtTokenProvider(@Value("${app.jwt.secret:404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970}") String jwtSecret) {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, keyBytes.length);
            keyBytes = padded;
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (Exception ex) {
            log.warn("Invalid JWT token: {}", ex.getMessage());
            return false;
        }
    }

    public String extractUserId(String token) {
        Claims claims = getClaims(token);
        String userId = claims.get("userId", String.class);
        return (userId != null && !userId.isBlank()) ? userId : claims.getSubject();
    }

    public String extractEmail(String token) {
        Claims claims = getClaims(token);
        String email = claims.get("email", String.class);
        return (email != null && !email.isBlank()) ? email : claims.getSubject();
    }

    public String extractRole(String token) {
        Claims claims = getClaims(token);
        String role = claims.get("role", String.class);
        if (role == null || role.isBlank()) {
            role = claims.get("roles", String.class);
        }
        return (role != null) ? role : "CUSTOMER";
    }

    public String extractSessionId(String token) {
        Claims claims = getClaims(token);
        return claims.get("sid", String.class);
    }

    private Claims getClaims(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
