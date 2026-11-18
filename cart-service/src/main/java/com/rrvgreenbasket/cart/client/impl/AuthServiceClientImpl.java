package com.rrvgreenbasket.cart.client.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrvgreenbasket.cart.client.AuthServiceClient;
import com.rrvgreenbasket.cart.security.S2SSignatureUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

/**
 * AuthServiceClientImpl
 *
 * <p><strong>What it does:</strong></p>
 * Invokes Auth Service canonical S2S endpoint /api/auth/internal/sessions/{sessionId}/validate
 * to verify real-time session revocation status.
 */
@Slf4j
@Component
public class AuthServiceClientImpl implements AuthServiceClient {

    private final RestClient restClient;
    private final S2SSignatureUtil signatureUtil;
    private final ObjectMapper objectMapper;
    private final String internalSecret;
    private final String serviceId;

    public AuthServiceClientImpl(
            S2SSignatureUtil signatureUtil,
            ObjectMapper objectMapper,
            @Value("${app.services.auth-service-url:http://localhost:8081}") String authServiceBaseUrl,
            @Value("${app.security.internal-service-secret:c8f7e2d1a5b49382716054321fedcba9876543210abcdef0123456789abcdef0}") String internalSecret,
            @Value("${app.security.internal-service-id:cart-service}") String serviceId) {

        this.signatureUtil = signatureUtil;
        this.objectMapper = objectMapper;
        this.internalSecret = internalSecret;
        this.serviceId = serviceId;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(2000);
        requestFactory.setReadTimeout(3000);

        String resolvedBaseUrl = authServiceBaseUrl.replaceAll("/+$", "").replace("localhost", "127.0.0.1");

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(resolvedBaseUrl)
                .build();
    }

    @Override
    public boolean isSessionActive(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return false;
        }

        String path = "/api/auth/internal/sessions/" + sessionId + "/validate";
        long timestamp = System.currentTimeMillis();
        String nonce = UUID.randomUUID().toString();
        String bodyHash = signatureUtil.calculateBodyHash(new byte[0]);
        String canonicalString = signatureUtil.buildCanonicalString("GET", path, timestamp, nonce, bodyHash);
        String signature = signatureUtil.calculateSignature(canonicalString, internalSecret);

        try {
            String jsonResponse = restClient.get()
                    .uri(path)
                    .header("X-Internal-Service-Id", serviceId)
                    .header("X-Internal-Key-Id", "v1")
                    .header("X-Internal-Timestamp", String.valueOf(timestamp))
                    .header("X-Internal-Nonce", nonce)
                    .header("X-Internal-Signature", signature)
                    .retrieve()
                    .body(String.class);

            if (jsonResponse != null) {
                JsonNode root = objectMapper.readTree(jsonResponse);
                boolean isValid = root.path("valid").asBoolean(false);
                log.debug("Session validation probe for sessionId='{}': valid={}", sessionId, isValid);
                return isValid;
            }
            return false;
        } catch (Exception ex) {
            log.warn("Auth Service session validation failed for sessionId='{}': {}", sessionId, ex.getMessage());
            return false;
        }
    }
}
