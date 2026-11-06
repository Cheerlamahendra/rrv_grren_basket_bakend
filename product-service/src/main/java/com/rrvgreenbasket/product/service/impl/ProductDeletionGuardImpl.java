package com.rrvgreenbasket.product.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrvgreenbasket.product.exception.ConflictException;
import com.rrvgreenbasket.product.exception.DependencyServiceUnavailableException;
import com.rrvgreenbasket.product.service.ProductDeletionGuard;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Service
public class ProductDeletionGuardImpl implements ProductDeletionGuard {

    private final RestTemplate restTemplate;
    private final String orderServiceUrl;
    private final String inventoryServiceUrl;
    private final SecretKey internalKey;
    private final ObjectMapper objectMapper;

    @Autowired
    public ProductDeletionGuardImpl(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${app.services.order-service-url:http://localhost:8082}") String orderServiceUrl,
            @Value("${app.services.inventory-service-url:http://localhost:8085}") String inventoryServiceUrl,
            @Value("${app.security.internal-service-secret:c8f7e2d1a5b49382716054321fedcba9876543210abcdef0123456789abcdef0}") String internalSecret,
            ObjectMapper objectMapper) {

        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofSeconds(5))
                .build();
        this.orderServiceUrl = orderServiceUrl.replaceAll("/+$", "");
        this.inventoryServiceUrl = inventoryServiceUrl.replaceAll("/+$", "");
        this.objectMapper = objectMapper;

        byte[] keyBytes = internalSecret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, keyBytes.length);
            keyBytes = padded;
        }
        this.internalKey = Keys.hmacShaKeyFor(keyBytes);
    }

    @Override
    public void validateProductCanBeDeleted(String productPublicId) {
        if (productPublicId == null || productPublicId.isBlank()) {
            throw new IllegalArgumentException("Product public ID cannot be blank for deletion validation");
        }

        String trimmedId = productPublicId.trim();
        log.info("Executing pre-deletion dependency checks for product: {}", trimmedId);

        // 1. Order Service Deletion Guard Check
        checkOrderService(trimmedId);

        // 2. Inventory Service Deletion Guard Check
        checkInventoryService(trimmedId);

        log.info("Product {} passed all pre-deletion dependency checks successfully", trimmedId);
    }

    private void checkOrderService(String productPublicId) {
        String url = orderServiceUrl + "/api/internal/orders/products/" + productPublicId + "/deletion-check";
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        headers.set("X-Gateway-Assertion", generateGatewayAssertion());
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            log.debug("Calling Order Service deletion check at: {}", url);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode dataNode = root.has("data") ? root.get("data") : root;
                boolean canDelete = dataNode.path("canDelete").asBoolean(true);
                if (!canDelete) {
                    String reason = dataNode.path("reason").asText("historical orders exist for product");
                    throw new ConflictException("Cannot permanently delete product: " + reason);
                }
            }
        } catch (ConflictException ex) {
            throw ex;
        } catch (HttpStatusCodeException ex) {
            if (ex.getStatusCode() == HttpStatus.CONFLICT) {
                throw new ConflictException("Cannot permanently delete product: historical orders exist for product public ID " + productPublicId);
            }
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                log.debug("Product {} not found in Order Service, safe to proceed", productPublicId);
                return;
            }
            log.error("Order Service returned error HTTP {}: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new DependencyServiceUnavailableException(
                    "Order Service is unavailable or returned error (HTTP " + ex.getStatusCode().value() + "). Permanent product deletion blocked for safety.");
        } catch (ResourceAccessException | java.io.IOException ex) {
            log.error("Failed to connect to Order Service at {}: {}", url, ex.getMessage());
            throw new DependencyServiceUnavailableException(
                    "Order Service is unavailable. Permanent product deletion blocked for safety.", ex);
        } catch (Exception ex) {
            log.error("Unexpected error checking Order Service deletion status: {}", ex.getMessage());
            throw new DependencyServiceUnavailableException(
                    "Order Service is unavailable. Permanent product deletion blocked for safety.", ex);
        }
    }

    private void checkInventoryService(String productPublicId) {
        String url = inventoryServiceUrl + "/api/internal/inventory/products/" + productPublicId + "/deletion-check";
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        headers.set("X-Gateway-Assertion", generateGatewayAssertion());
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            log.debug("Calling Inventory Service deletion check at: {}", url);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode dataNode = root.has("data") ? root.get("data") : root;
                boolean canDelete = dataNode.path("canDelete").asBoolean(true);
                if (!canDelete) {
                    String reason = dataNode.path("reason").asText("active inventory or historical movement records exist");
                    throw new ConflictException("Cannot permanently delete product: " + reason);
                }
            }
        } catch (ConflictException ex) {
            throw ex;
        } catch (HttpStatusCodeException ex) {
            if (ex.getStatusCode() == HttpStatus.CONFLICT) {
                throw new ConflictException("Cannot permanently delete product: inventory dependencies prevent deletion for product public ID " + productPublicId);
            }
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                log.debug("Product {} not found in Inventory Service, safe to proceed", productPublicId);
                return;
            }
            log.error("Inventory Service returned error HTTP {}: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new DependencyServiceUnavailableException(
                    "Inventory Service is unavailable or returned error (HTTP " + ex.getStatusCode().value() + "). Permanent product deletion blocked for safety.");
        } catch (ResourceAccessException | java.io.IOException ex) {
            log.error("Failed to connect to Inventory Service at {}: {}", url, ex.getMessage());
            throw new DependencyServiceUnavailableException(
                    "Inventory Service is unavailable. Permanent product deletion blocked for safety.", ex);
        } catch (Exception ex) {
            log.error("Unexpected error checking Inventory Service deletion status: {}", ex.getMessage());
            throw new DependencyServiceUnavailableException(
                    "Inventory Service is unavailable. Permanent product deletion blocked for safety.", ex);
        }
    }

    private String generateGatewayAssertion() {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject("PRODUCT-SERVICE")
                .issuer("rrv-gateway-internal")
                .audience().add("rrv-backend-internal").and()
                .claim("role", "ROLE_INTERNAL_SERVICE")
                .claim("sid", UUID.randomUUID().toString())
                .issuedAt(new Date(now))
                .expiration(new Date(now + 60_000))
                .signWith(internalKey, Jwts.SIG.HS256)
                .compact();
    }
}
