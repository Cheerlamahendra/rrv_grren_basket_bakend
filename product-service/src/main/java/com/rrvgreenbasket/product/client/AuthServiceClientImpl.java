
package com.rrvgreenbasket.product.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.UUID;

@Slf4j
@Component
public class AuthServiceClientImpl implements AuthServiceClient {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String KEY_ID = "v1";

    private final RestTemplate restTemplate;
    private final String authServiceUrl;
    private final ObjectMapper objectMapper;
    private final String internalSecret;
    private final String serviceId;

    public AuthServiceClientImpl(
            @Value("${app.services.auth-service-url:http://localhost:8081}")
            String authServiceUrl,

            @Value("${app.security.internal-service-secret}")
            String internalSecret,

            @Value("${app.security.internal-service-id:product-service}")
            String serviceId,

            ObjectMapper objectMapper) {

        if (internalSecret == null || internalSecret.isBlank()) {
            throw new IllegalStateException(
                    "Internal service secret is not configured"
            );
        }

        this.restTemplate = new RestTemplate();
        this.authServiceUrl = authServiceUrl.replaceAll("/+$", "");
        this.internalSecret = internalSecret;
        this.serviceId = serviceId;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean isSessionActive(String sessionId) {

        if (sessionId == null || sessionId.isBlank()) {
            log.warn("Session validation rejected: sessionId is missing");
            return false;
        }

        String path = "/api/auth/internal/sessions/"
                + sessionId.trim()
                + "/validate";

        try {
            // GET request has an empty request body.
            String bodyHash = calculateBodyHash(new byte[0]);

            long timestamp = System.currentTimeMillis();
            String nonce = UUID.randomUUID().toString();

            // Must match Auth Service canonical string exactly.
            String canonicalString = buildCanonicalString(
                    HttpMethod.GET.name(),
                    path,
                    timestamp,
                    nonce,
                    bodyHash
            );

            String signature = calculateSignature(
                    canonicalString,
                    internalSecret
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));

            headers.set("X-Internal-Service-Id", serviceId);
            headers.set("X-Internal-Key-Id", KEY_ID);
            headers.set("X-Internal-Timestamp", String.valueOf(timestamp));
            headers.set("X-Internal-Nonce", nonce);
            headers.set("X-Internal-Signature", signature);

            HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

            String url = authServiceUrl + path;

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    requestEntity,
                    String.class
            );

            if (!response.getStatusCode().is2xxSuccessful()
                    || response.getBody() == null) {

                log.warn(
                        "Session validation failed: Auth Service returned HTTP {}",
                        response.getStatusCode()
                );

                return false;
            }

            JsonNode root = objectMapper.readTree(response.getBody());

            boolean valid = root.path("valid").asBoolean(false);

            log.debug(
                    "Session validation completed. sessionId={}, valid={}",
                    sessionId,
                    valid
            );

            return valid;

        } catch (Exception e) {

            // Fail closed: never accept a session when validation fails.
            log.error(
                    "Auth Service session validation failed for sessionId={}: {}",
                    sessionId,
                    e.getMessage()
            );

            return false;
        }
    }

    private String calculateBodyHash(byte[] bodyBytes) {

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] hashBytes = digest.digest(
                    bodyBytes != null ? bodyBytes : new byte[0]
            );

            return HexFormat.of().formatHex(hashBytes);

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to calculate request body hash",
                    e
            );
        }
    }

    private String buildCanonicalString(
            String method,
            String path,
            long timestamp,
            String nonce,
            String bodyHash) {

        return method.toUpperCase() + "\n"
                + path + "\n"
                + timestamp + "\n"
                + nonce + "\n"
                + bodyHash;
    }

    private String calculateSignature(
            String canonicalString,
            String secret) {

        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);

            SecretKeySpec secretKey = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8),
                    HMAC_ALGORITHM
            );

            mac.init(secretKey);

            byte[] signatureBytes = mac.doFinal(
                    canonicalString.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(signatureBytes);

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to generate internal service signature",
                    e
            );
        }
    }
}
