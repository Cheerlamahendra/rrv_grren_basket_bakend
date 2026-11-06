package com.rrvgreenbasket.gateway.util;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * GatewayAssertionUtil
 *
 * <p><strong>What it does:</strong></p>
 * Generates short-lived (30s) cryptographically signed assertion JWTs
 * binding verified user identity, role, session ID, HTTP method, path, and body hash.
 * Injected as <code>X-Gateway-Assertion</code> header downstream.
 */
@Component
public class GatewayAssertionUtil {

    private final SecretKey signingKey;
    private final String issuer;

    public GatewayAssertionUtil(
            @Value("${app.security.internal-service-secret:c8f7e2d1a5b49382716054321fedcba9876543210abcdef0123456789abcdef0}") String secret,
            @Value("${app.jwt.issuer:rrv-gateway}") String issuer) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, keyBytes.length);
            keyBytes = padded;
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.issuer = issuer;
    }

    public String generateAssertion(String userId, String role, String sessionId, String method, String path, String bodyHash) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + 30_000L); // 30 seconds TTL

        return Jwts.builder()
                .issuer(issuer)
                .audience().add("rrv-internal-services").and()
                .subject(userId)
                .id(UUID.randomUUID().toString()) // jti nonce
                .claim("role", role)
                .claim("sid", sessionId)
                .claim("m", method != null ? method.toUpperCase() : "GET")
                .claim("p", path != null ? path : "/")
                .claim("bh", bodyHash != null ? bodyHash : "")
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(signingKey)
                .compact();
    }
}
