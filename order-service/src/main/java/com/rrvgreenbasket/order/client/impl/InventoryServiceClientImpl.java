package com.rrvgreenbasket.order.client.impl;

import com.rrvgreenbasket.order.client.InventoryServiceClient;
import com.rrvgreenbasket.order.client.dto.ReservationClientResponse;
import com.rrvgreenbasket.order.client.dto.ServiceApiResponse;
import com.rrvgreenbasket.order.client.dto.StockReservationClientRequest;
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
public class InventoryServiceClientImpl implements InventoryServiceClient {

    private final RestClient restClient;
    private final SecretKey internalKey;

    @Autowired
    public InventoryServiceClientImpl(
            @Value("${app.services.inventory-service-url:http://localhost:8085}") String inventoryServiceUrl,
            @Value("${app.security.internal-service-secret:c8f7e2d1a5b49382716054321fedcba9876543210abcdef0123456789abcdef0}") String secret,
            @Value("${app.client.connect-timeout-ms:3000}") int connectTimeout,
            @Value("${app.client.read-timeout-ms:5000}") int readTimeout) {

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);

        this.restClient = RestClient.builder()
                .baseUrl(inventoryServiceUrl.replaceAll("/+$", ""))
                .requestFactory(factory)
                .build();

        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, keyBytes.length);
            keyBytes = padded;
        }
        this.internalKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public InventoryServiceClientImpl(RestClient restClient, String secret) {
        this.restClient = restClient;
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, keyBytes.length);
            keyBytes = padded;
        }
        this.internalKey = Keys.hmacShaKeyFor(keyBytes);
    }

    @Override
    public ReservationClientResponse createReservation(StockReservationClientRequest request) {
        log.info("Requesting stock reservation for order: {}", request.getOrderPublicId());
        try {
            ServiceApiResponse<ReservationClientResponse> response = restClient.post()
                    .uri("/api/internal/inventory/reservations")
                    .header("X-Gateway-Assertion", generateInternalAssertion())
                    .body(request)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            if (response == null || !Boolean.TRUE.equals(response.success()) || response.data() == null) {
                throw new OrderValidationException("Stock reservation failed with Inventory Service");
            }
            return response.data();
        } catch (HttpClientErrorException.Conflict | HttpClientErrorException.BadRequest e) {
            log.warn("Inventory insufficient for order {}: {}", request.getOrderPublicId(), e.getResponseBodyAsString());
            throw new OrderValidationException("One or more items in your cart do not have sufficient stock available: " + e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Stock reservation error for order {}: {}", request.getOrderPublicId(), e.getMessage());
            throw new ExternalServiceException("Inventory Service is currently unavailable for stock reservation: " + e.getMessage(), e);
        }
    }

    @Override
    public ReservationClientResponse confirmReservation(String reservationIdentifier) {
        log.info("Confirming stock reservation: {}", reservationIdentifier);
        try {
            ServiceApiResponse<ReservationClientResponse> response = restClient.post()
                    .uri("/api/internal/inventory/reservations/{id}/confirm", reservationIdentifier)
                    .header("X-Gateway-Assertion", generateInternalAssertion())
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            if (response != null && response.data() != null) {
                return response.data();
            }
            return ReservationClientResponse.builder().orderPublicId(reservationIdentifier).status("CONFIRMED").build();
        } catch (Exception e) {
            log.error("Failed to confirm stock reservation for {}: {}", reservationIdentifier, e.getMessage());
            throw new ExternalServiceException("Failed to confirm inventory reservation: " + e.getMessage(), e);
        }
    }

    @Override
    public ReservationClientResponse releaseReservation(String reservationIdentifier) {
        log.info("Releasing stock reservation for order/reservation: {}", reservationIdentifier);
        try {
            ServiceApiResponse<ReservationClientResponse> response = restClient.post()
                    .uri("/api/internal/inventory/reservations/{id}/release", reservationIdentifier)
                    .header("X-Gateway-Assertion", generateInternalAssertion())
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            if (response != null && response.data() != null) {
                return response.data();
            }
            return ReservationClientResponse.builder().orderPublicId(reservationIdentifier).status("RELEASED").build();
        } catch (Exception e) {
            log.warn("Non-fatal: failed to release reservation for {}: {}", reservationIdentifier, e.getMessage());
            return ReservationClientResponse.builder().orderPublicId(reservationIdentifier).status("RELEASE_PENDING").build();
        }
    }

    private String generateInternalAssertion() {
        Date now = new Date();
        return Jwts.builder()
                .subject("order-service")
                .claim("role", "ROLE_INTERNAL_SERVICE")
                .claim("sid", "s2s-order-" + UUID.randomUUID())
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 60_000L))
                .signWith(internalKey)
                .compact();
    }
}
