package com.rrvgreenbasket.order.client.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrvgreenbasket.order.client.CartServiceClient;
import com.rrvgreenbasket.order.client.dto.CartClientResponse;
import com.rrvgreenbasket.order.client.dto.CartValidationClientResponse;
import com.rrvgreenbasket.order.client.dto.ServiceApiResponse;
import com.rrvgreenbasket.order.exception.ExternalServiceException;
import com.rrvgreenbasket.order.exception.OrderValidationException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Component
public class CartServiceClientImpl implements CartServiceClient {

    private final RestClient restClient;
    private final SecretKey internalKey;
    private final String issuer;
    private final ObjectMapper objectMapper;

    @Autowired
    public CartServiceClientImpl(
            @Value("${app.services.cart-service-url:http://localhost:8086}") String cartServiceUrl,
            @Value("${app.security.internal-service-secret:RQpmB5hGO2NsLNE7OnwXo5NfNtEJj+j+461g0oEeGunJPZ4iYQEGr+uk5M3hAfgDg26IDtmA9N2RJbFXkCLrqQ==}") String secret,
            @Value("${app.jwt.issuer:rrv-gateway}") String issuer,
            @Value("${app.client.connect-timeout-ms:3000}") int connectTimeout,
            @Value("${app.client.read-timeout-ms:5000}") int readTimeout,
            @Autowired(required = false) ObjectMapper objectMapper) {

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);

        this.restClient = RestClient.builder()
                .baseUrl(cartServiceUrl.replaceAll("/+$", ""))
                .requestFactory(factory)
                .build();

        this.internalKey = buildSecretKey(secret);
        this.issuer = issuer;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    public CartServiceClientImpl(RestClient restClient, String secret, String issuer) {
        this(restClient, secret, issuer, new ObjectMapper());
    }

    public CartServiceClientImpl(RestClient restClient, String secret, String issuer, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.internalKey = buildSecretKey(secret);
        this.issuer = issuer;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    private static SecretKey buildSecretKey(String secret) {
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

        return Keys.hmacShaKeyFor(keyBytes);
    }

    @Override
    public CartClientResponse getCustomerCart(String customerPublicId) {
        log.debug("Fetching active cart for customer: {}", customerPublicId);
        try {
            String rawJson = restClient.get()
                    .uri("/api/cart")
                    .header("X-Gateway-Assertion", generateCustomerAssertion(customerPublicId, "GET", "/api/cart"))
                    .retrieve()
                    .body(String.class);

            if (!StringUtils.hasText(rawJson)) {
                throw new OrderValidationException("Customer cart is empty or could not be loaded");
            }

            JsonNode root = objectMapper.readTree(rawJson);
            CartClientResponse cart;
            if (root.has("data") && !root.get("data").isNull()) {
                cart = objectMapper.treeToValue(root.get("data"), CartClientResponse.class);
            } else {
                cart = objectMapper.treeToValue(root, CartClientResponse.class);
            }

            if (cart == null) {
                throw new OrderValidationException("Customer cart is empty or could not be loaded");
            }
            return cart;
        } catch (HttpClientErrorException.NotFound e) {
            throw new OrderValidationException("Customer does not have an active shopping cart");
        } catch (OrderValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to fetch customer cart from Cart Service: {}", e.getMessage());
            throw new ExternalServiceException("Cart Service unavailable: " + e.getMessage(), e);
        }
    }

    @Override
    public CartValidationClientResponse validateCart(String customerPublicId) {
        log.debug("Validating cart with Cart Service for customer: {}", customerPublicId);
        try {
            String rawJson = restClient.post()
                    .uri("/api/cart/validate")
                    .header("X-Gateway-Assertion", generateCustomerAssertion(customerPublicId, "POST", "/api/cart/validate"))
                    .retrieve()
                    .body(String.class);

            if (!StringUtils.hasText(rawJson)) {
                return CartValidationClientResponse.builder().isValid(true).build();
            }

            JsonNode root = objectMapper.readTree(rawJson);
            if (root.has("data") && !root.get("data").isNull()) {
                return objectMapper.treeToValue(root.get("data"), CartValidationClientResponse.class);
            } else {
                return objectMapper.treeToValue(root, CartValidationClientResponse.class);
            }
        } catch (HttpClientErrorException.NotFound e) {
            log.info("Cart Service '/api/cart/validate' not present (404). Proceeding with order-service live item checks.");
            return CartValidationClientResponse.builder().isValid(true).build();
        } catch (OrderValidationException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Cart validation call returned warning: {}. Falling back to individual product verification.", e.getMessage());
            return CartValidationClientResponse.builder().isValid(true).build();
        }
    }

    @Override
    public void clearCart(String customerPublicId) {
        log.debug("Clearing active cart for customer: {}", customerPublicId);
        try {
            restClient.delete()
                    .uri("/api/cart")
                    .header("X-Gateway-Assertion", generateCustomerAssertion(customerPublicId, "DELETE", "/api/cart"))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Successfully cleared cart for customer: {}", customerPublicId);
        } catch (Exception e) {
            log.warn("Non-fatal: failed to clear cart for customer '{}' after order creation: {}", customerPublicId, e.getMessage());
        }
    }

    private String generateCustomerAssertion(String customerPublicId, String method, String path) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + 30_000L);

        return Jwts.builder()
                .issuer(issuer)
                .audience().add("rrv-internal-services").and()
                .subject(customerPublicId)
                .id(UUID.randomUUID().toString())
                .claim("role", "CUSTOMER")
                .claim("sid", "order-saga-" + UUID.randomUUID())
                .claim("m", method)
                .claim("p", path)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(internalKey, Jwts.SIG.HS512)
                .compact();
    }
}
