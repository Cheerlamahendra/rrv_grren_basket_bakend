package com.rrvgreenbasket.customer.client.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrvgreenbasket.customer.client.AuthServiceClient;
import com.rrvgreenbasket.customer.security.S2SSignatureUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

/**
 * AuthServiceClientImpl
 *
 * <p><strong>What it does:</strong></p>
 * Invokes Auth Service internal session validation endpoint using canonical HMAC-SHA256 signing.
 * Enables direct Customer Service requests (e.g. testing) to check live session revocation.
 */
@Component
public class AuthServiceClientImpl implements AuthServiceClient {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceClientImpl.class);

    private final RestClient restClient;
    private final S2SSignatureUtil signatureUtil;
    private final ObjectMapper objectMapper;
    private final String authServiceBaseUrl;
    private final String internalSecret;
    private final String serviceId;

    public AuthServiceClientImpl(
            S2SSignatureUtil signatureUtil,
            ObjectMapper objectMapper,
            @Value("${app.services.auth-service-url:http://localhost:8081}") String authServiceBaseUrl,
            @Value("${app.security.internal-service-secret:c8f7e2d1a5b49382716054321fedcba9876543210abcdef0123456789abcdef0}") String internalSecret,
            @Value("${app.security.internal-service-id:customer-service}") String serviceId) {

        this.signatureUtil = signatureUtil;
        this.objectMapper = objectMapper;
        this.authServiceBaseUrl = authServiceBaseUrl.replaceAll("/+$", "");
        this.internalSecret = internalSecret;
        this.serviceId = serviceId;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(2000);
        requestFactory.setReadTimeout(3000);

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(this.authServiceBaseUrl)
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
