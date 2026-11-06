package com.rrvgreenbasket.inventory.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrvgreenbasket.inventory.client.dto.ProductVerificationResponse;
import com.rrvgreenbasket.inventory.dto.response.ApiResponse;
import com.rrvgreenbasket.inventory.exception.ProductNotFoundException;
import com.rrvgreenbasket.inventory.exception.ProductServiceUnavailableException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class ProductServiceClientImpl implements ProductServiceClient {

    private final RestTemplate restTemplate;
    private final String productServiceUrl;
    private final ObjectMapper objectMapper;
    private final SecretKey internalKey;

    public ProductServiceClientImpl(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${app.services.product-service-url:http://localhost:8084}") String productServiceUrl,
            @Value("${app.security.internal-service-secret:c8f7e2d1a5b49382716054321fedcba9876543210abcdef0123456789abcdef0}") String internalSecret,
            ObjectMapper objectMapper) {

        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofSeconds(5))
                .build();
        this.productServiceUrl = productServiceUrl.replaceAll("/+$", "");
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
    public ProductVerificationResponse getProductByPublicId(String productPublicId) {
        if (productPublicId == null || productPublicId.isBlank()) {
            throw new ProductNotFoundException("Product public ID cannot be null or empty");
        }

        String url = productServiceUrl + "/api/admin/products/" + productPublicId.trim();

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));

            // Generate cryptographic Gateway Assertion with ROLE_ADMIN
            String assertion = generateGatewayAssertion();
            headers.set("X-Gateway-Assertion", assertion);

            // Forward caller's Authorization header if present
            forwardAuthorizationHeaderIfPresent(headers);

            HttpEntity<Void> requestEntity = new HttpEntity<>(headers);
            log.debug("Calling Product Service: {}", url);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    requestEntity,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                ApiResponse<ProductVerificationResponse> apiResponse = objectMapper.readValue(
                        response.getBody(),
                        new TypeReference<ApiResponse<ProductVerificationResponse>>() {}
                );

                if (apiResponse != null && apiResponse.getData() != null) {
                    ProductVerificationResponse product = apiResponse.getData();
                    log.debug("Successfully verified product '{}' (unit={})", product.getPublicId(), product.getQuantityUnit());
                    return product;
                }
            }

            throw new ProductNotFoundException("Product not found with public ID: " + productPublicId);

        } catch (HttpClientErrorException.NotFound ex) {
            log.warn("Product Service returned 404 for product: {}", productPublicId);
            throw new ProductNotFoundException("Product not found with public ID: " + productPublicId);
        } catch (ResourceAccessException | HttpServerErrorException ex) {
            log.error("Failed to connect to Product Service at '{}': {}", url, ex.getMessage());
            throw new ProductServiceUnavailableException("Product Service is currently unavailable. Please try again later.");
        } catch (Exception ex) {
            if (ex instanceof ProductNotFoundException || ex instanceof ProductServiceUnavailableException) {
                throw (RuntimeException) ex;
            }
            log.error("Unexpected error validating product '{}' with Product Service: {}", productPublicId, ex.getMessage());
            throw new ProductServiceUnavailableException("Unable to verify product details with Product Service: " + ex.getMessage());
        }
    }

    private String generateGatewayAssertion() {
        return Jwts.builder()
                .subject("inventory-service")
                .claim("role", "ROLE_ADMIN")
                .claim("sid", "")
                .id(UUID.randomUUID().toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(internalKey)
                .compact();
    }

    private void forwardAuthorizationHeaderIfPresent(HttpHeaders headers) {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest currentRequest = attributes.getRequest();
                String authHeader = currentRequest.getHeader("Authorization");
                if (authHeader != null && !authHeader.isBlank()) {
                    headers.set("Authorization", authHeader);
                }
            }
        } catch (Exception ignored) {
            // Context not available in background tasks or tests
        }
    }
}
