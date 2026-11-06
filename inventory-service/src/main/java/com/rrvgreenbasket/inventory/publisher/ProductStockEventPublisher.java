package com.rrvgreenbasket.inventory.publisher;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Component
public class ProductStockEventPublisher {

    private final RestTemplate restTemplate;
    private final String productServiceUrl;
    private final SecretKey internalKey;

    public ProductStockEventPublisher(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${app.services.product-service-url:http://localhost:8084}") String productServiceUrl,
            @Value("${app.security.internal-service-secret:c8f7e2d1a5b49382716054321fedcba9876543210abcdef0123456789abcdef0}") String internalSecret) {

        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofSeconds(5))
                .build();
        this.productServiceUrl = productServiceUrl.replaceAll("/+$", "");

        byte[] keyBytes = internalSecret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, keyBytes.length);
            keyBytes = padded;
        }
        this.internalKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public boolean publishStockEvent(String jsonPayload) throws Exception {
        String url = productServiceUrl + "/api/internal/products/stock-events";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Gateway-Assertion", generateGatewayAssertion());

        HttpEntity<String> entity = new HttpEntity<>(jsonPayload, headers);

        try {
            log.debug("Dispatching stock event outbox to: {}", url);
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (HttpStatusCodeException ex) {
            log.warn("Product Service rejected stock event with HTTP {}: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new RuntimeException("HTTP " + ex.getStatusCode().value() + ": " + ex.getStatusText(), ex);
        } catch (Exception ex) {
            log.warn("Failed to deliver stock event to Product Service: {}", ex.getMessage());
            throw ex;
        }
    }

    private String generateGatewayAssertion() {
        InstantNowHolder now = new InstantNowHolder();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject("INVENTORY-SERVICE")
                .issuer("rrv-gateway-internal")
                .audience().add("rrv-backend-internal").and()
                .claim("role", "ROLE_INTERNAL_SERVICE")
                .claim("sid", UUID.randomUUID().toString())
                .issuedAt(new Date(now.epochMs))
                .expiration(new Date(now.epochMs + 60_000))
                .signWith(internalKey, Jwts.SIG.HS256)
                .compact();
    }

    private static class InstantNowHolder {
        final long epochMs = System.currentTimeMillis();
    }
}
