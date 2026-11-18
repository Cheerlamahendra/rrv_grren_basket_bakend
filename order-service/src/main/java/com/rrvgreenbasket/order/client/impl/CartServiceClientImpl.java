package com.rrvgreenbasket.order.client.impl;

import com.rrvgreenbasket.order.client.CartServiceClient;
import com.rrvgreenbasket.order.client.dto.CartClientResponse;
import com.rrvgreenbasket.order.client.dto.CartValidationClientResponse;
import com.rrvgreenbasket.order.client.dto.ServiceApiResponse;
import com.rrvgreenbasket.order.exception.ExternalServiceException;
import com.rrvgreenbasket.order.exception.OrderValidationException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
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

    @Autowired
    public CartServiceClientImpl(
            @Value("${app.services.cart-service-url:http://localhost:8086}") String cartServiceUrl,
            @Value("${app.security.internal-service-secret:c8f7e2d1a5b49382716054321fedcba9876543210abcdef0123456789abcdef0}") String secret,
            @Value("${app.jwt.issuer:rrv-gateway}") String issuer,
            @Value("${app.client.connect-timeout-ms:3000}") int connectTimeout,
            @Value("${app.client.read-timeout-ms:5000}") int readTimeout) {

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);

        this.restClient = RestClient.builder()
                .baseUrl(cartServiceUrl.replaceAll("/+$", ""))
                .requestFactory(factory)
                .build();

        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, keyBytes.length);
            keyBytes = padded;
        }
        this.internalKey = Keys.hmacShaKeyFor(keyBytes);
        this.issuer = issuer;
    }

    public CartServiceClientImpl(RestClient restClient, String secret, String issuer) {
        this.restClient = restClient;
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, keyBytes.length);
            keyBytes = padded;
        }
        this.internalKey = Keys.hmacShaKeyFor(keyBytes);
        this.issuer = issuer;
    }

    @Override
    public CartClientResponse getCustomerCart(String customerPublicId) {
        log.debug("Fetching active cart for customer: {}", customerPublicId);
        try {
            ServiceApiResponse<CartClientResponse> response = restClient.get()
                    .uri("/api/cart")
                    .header("X-Gateway-Assertion", generateCustomerAssertion(customerPublicId, "GET", "/api/cart"))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            if (response == null || !Boolean.TRUE.equals(response.success()) || response.data() == null) {
                throw new OrderValidationException("Customer cart is empty or could not be loaded");
            }
            return response.data();
        } catch (HttpClientErrorException.NotFound e) {
            throw new OrderValidationException("Customer does not have an active shopping cart");
        } catch (Exception e) {
            log.error("Failed to fetch customer cart from Cart Service: {}", e.getMessage());
            throw new ExternalServiceException("Cart Service unavailable: " + e.getMessage(), e);
        }
    }

    @Override
    public CartValidationClientResponse validateCart(String customerPublicId) {
        log.debug("Validating cart with Cart Service for customer: {}", customerPublicId);
        try {
            ServiceApiResponse<CartValidationClientResponse> response = restClient.post()
                    .uri("/api/cart/validate")
                    .header("X-Gateway-Assertion", generateCustomerAssertion(customerPublicId, "POST", "/api/cart/validate"))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            if (response == null || !Boolean.TRUE.equals(response.success()) || response.data() == null) {
                throw new OrderValidationException("Cart validation failed with Cart Service");
            }
            return response.data();
        } catch (Exception e) {
            log.error("Cart validation failed against Cart Service: {}", e.getMessage());
            throw new ExternalServiceException("Unable to validate cart with Cart Service: " + e.getMessage(), e);
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
                .signWith(internalKey)
                .compact();
    }
}
