package com.rrvgreenbasket.gateway.util;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

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
            @Value("${app.security.internal-service-secret:RQpmB5hGO2NsLNE7OnwXo5NfNtEJj+j+461g0oEeGunJPZ4iYQEGr+uk5M3hAfgDg26IDtmA9N2RJbFXkCLrqQ==}") String secret,
            @Value("${app.jwt.issuer:rrv-gateway}") String issuer) {

        if (!StringUtils.hasText(secret)) {
            throw new IllegalStateException(
                    "Gateway assertion secret property 'app.security.internal-service-secret' is missing or blank. A 512-bit Base64-encoded secret is required for HS512."
            );
        }

        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(secret.trim());
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Gateway assertion secret property 'app.security.internal-service-secret' is not valid Base64: " + e.getMessage(), e
            );
        }

        if (keyBytes.length < 64) {
            throw new IllegalStateException(
                    "Gateway assertion secret must be at least 512 bits (64 bytes) for HS512. Decoded key size is " + (keyBytes.length * 8) + " bits."
            );
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
                .signWith(signingKey, Jwts.SIG.HS512)
                .compact();
    }
}
