package com.rrvgreenbasket.payment.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrvgreenbasket.payment.client.dto.OrderDetailsDto;
import com.rrvgreenbasket.payment.dto.response.ApiResponse;
import com.rrvgreenbasket.payment.exception.OrderNotFoundException;
import com.rrvgreenbasket.payment.exception.OrderServiceUnavailableException;
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
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class OrderServiceClientImpl implements OrderServiceClient {

    private final RestTemplate restTemplate;
    private final String orderServiceUrl;
    private final ObjectMapper objectMapper;
    private final SecretKey internalKey;
    private final boolean mockFallbackEnabled;

    public OrderServiceClientImpl(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${app.services.order-service-url:http://localhost:8088}") String orderServiceUrl,
            @Value("${app.security.internal-service-secret:c8f7e2d1a5b49382716054321fedcba9876543210abcdef0123456789abcdef0}") String internalSecret,
            @Value("${app.services.order-service.mock-fallback-enabled:true}") boolean mockFallbackEnabled,
            ObjectMapper objectMapper) {

        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofSeconds(5))
                .build();
        this.orderServiceUrl = orderServiceUrl.replaceAll("/+$", "");
        this.objectMapper = objectMapper;
        this.mockFallbackEnabled = mockFallbackEnabled;

        byte[] keyBytes = internalSecret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, keyBytes.length);
            keyBytes = padded;
        }
        this.internalKey = Keys.hmacShaKeyFor(keyBytes);
    }

    @Override
    public OrderDetailsDto getOrderForPayment(String orderPublicId) {
        if (orderPublicId == null || orderPublicId.isBlank()) {
            throw new OrderNotFoundException("Order public ID cannot be null or empty");
        }

        String url = orderServiceUrl + "/api/internal/orders/" + orderPublicId.trim();

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));

            // Generate cryptographic Gateway Assertion with ROLE_INTERNAL_SERVICE
            String assertion = generateGatewayAssertion();
            headers.set("X-Gateway-Assertion", assertion);

            forwardAuthorizationHeaderIfPresent(headers);

            HttpEntity<Void> requestEntity = new HttpEntity<>(headers);
            log.debug("Calling Order Service: {}", url);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    requestEntity,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                ApiResponse<OrderDetailsDto> apiResponse = objectMapper.readValue(
                        response.getBody(),
                        new TypeReference<ApiResponse<OrderDetailsDto>>() {}
                );

                if (apiResponse != null && apiResponse.getData() != null) {
                    OrderDetailsDto order = apiResponse.getData();
                    log.debug("Successfully fetched order '{}' (amount={}, status={})",
                            order.getOrderPublicId(), order.getTotalAmount(), order.getOrderStatus());
                    return order;
                }
            }

            throw new OrderNotFoundException("Order not found with public ID: " + orderPublicId);

        } catch (HttpClientErrorException.NotFound ex) {
            log.warn("Order Service returned 404 for order: {}", orderPublicId);
            throw new OrderNotFoundException("Order not found with public ID: " + orderPublicId);
        } catch (ResourceAccessException | HttpServerErrorException ex) {
            log.warn("Order Service unavailable at '{}': {}. Mock fallback enabled={}", url, ex.getMessage(), mockFallbackEnabled);
            if (mockFallbackEnabled) {
                return buildMockOrderDetails(orderPublicId);
            }
            throw new OrderServiceUnavailableException("Order Service is currently unavailable. Please try again later.");
        } catch (Exception ex) {
            if (ex instanceof OrderNotFoundException || ex instanceof OrderServiceUnavailableException) {
                throw (RuntimeException) ex;
            }
            log.warn("Error calling Order Service for order '{}': {}. Mock fallback enabled={}", orderPublicId, ex.getMessage(), mockFallbackEnabled);
            if (mockFallbackEnabled) {
                return buildMockOrderDetails(orderPublicId);
            }
            throw new OrderServiceUnavailableException("Unable to verify order details with Order Service: " + ex.getMessage());
        }
    }

    @Override
    public void notifyPaymentStatus(String orderPublicId, String eventType, Object payload) {
        String url = orderServiceUrl + "/api/internal/orders/" + orderPublicId.trim() + "/payment-events";
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Gateway-Assertion", generateGatewayAssertion());

            HttpEntity<Object> entity = new HttpEntity<>(payload, headers);
            restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            log.info("Successfully delivered event '{}' to Order Service for order '{}'", eventType, orderPublicId);
        } catch (Exception ex) {
            log.warn("Failed to notify Order Service of event '{}' for order '{}': {}. Outbox will retry.",
                    eventType, orderPublicId, ex.getMessage());
            throw new RuntimeException("Order Service event delivery failed: " + ex.getMessage(), ex);
        }
    }

    private OrderDetailsDto buildMockOrderDetails(String orderPublicId) {
        log.info("Generating trusted mock order details for order '{}' since Order Service is not yet running", orderPublicId);
        return OrderDetailsDto.builder()
                .orderPublicId(orderPublicId)
                .customerPublicId("customer-demo-123")
                .totalAmount(new BigDecimal("400.00"))
                .currency("INR")
                .orderStatus("PENDING_PAYMENT")
                .paymentEligible(true)
                .paymentMethod("ONLINE_FULL")
                .build();
    }

    private String generateGatewayAssertion() {
        return Jwts.builder()
                .subject("payment-service")
                .claim("role", "ROLE_INTERNAL_SERVICE")
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
