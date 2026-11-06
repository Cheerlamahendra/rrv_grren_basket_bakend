
package com.rrvgreenbasket.auth.client.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrvgreenbasket.auth.client.CustomerServiceClient;
import com.rrvgreenbasket.auth.security.S2SSignatureUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class CustomerServiceClientImpl implements CustomerServiceClient {

    private static final Logger log =
            LoggerFactory.getLogger(CustomerServiceClientImpl.class);

    private final RestClient restClient;
    private final S2SSignatureUtil signatureUtil;
    private final ObjectMapper objectMapper;
    private final String customerServiceBaseUrl;
    private final String internalSecret;
    private final String serviceId;

    public CustomerServiceClientImpl(
            S2SSignatureUtil signatureUtil,
            ObjectMapper objectMapper,
            @Value("${app.services.customer-service-url:http://localhost:8082}")
            String customerServiceBaseUrl,
            @Value("${app.security.internal-service-secret}")
            String internalSecret,
            @Value("${app.security.internal-service-id:auth-service}")
            String serviceId) {

        this.signatureUtil = signatureUtil;
        this.objectMapper = objectMapper;
        this.customerServiceBaseUrl =
                customerServiceBaseUrl.replaceAll("/+$", "");
        this.internalSecret = internalSecret;
        this.serviceId = serviceId;

        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();

        requestFactory.setConnectTimeout(3000);
        requestFactory.setReadTimeout(5000);

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(this.customerServiceBaseUrl)
                .build();
    }

    @Override
    public boolean initializeCustomerProfile(
            String authUserId,
            String fullName,
            String mobileNumber,
            String email) {

        String path = "/api/customers";

        Map<String, Object> payload = new HashMap<>();
        payload.put("authUserId", authUserId);
        payload.put("fullName", fullName);
        payload.put("mobileNumber", mobileNumber);

        if (email != null && !email.isBlank()) {
            payload.put("email", email.trim());
        }

        final byte[] bodyBytes;

        try {
            bodyBytes = objectMapper.writeValueAsBytes(payload);
        } catch (Exception e) {
            log.error(
                    "Failed to serialize customer profile payload for authUserId={}",
                    authUserId,
                    e
            );
            return false;
        }

        String bodyHash = signatureUtil.calculateBodyHash(bodyBytes);

        int maxRetries = 3;
        long backoffMs = 500L;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {

            long timestamp = System.currentTimeMillis();
            String nonce = UUID.randomUUID().toString();

            String canonicalString =
                    signatureUtil.buildCanonicalString(
                            "POST",
                            path,
                            timestamp,
                            nonce,
                            bodyHash
                    );

            String signature =
                    signatureUtil.calculateSignature(
                            canonicalString,
                            internalSecret
                    );

            try {
                log.info(
                        "Sending customer profile creation request. " +
                                "Attempt {}/{} for authUserId={}",
                        attempt,
                        maxRetries,
                        authUserId
                );

                restClient.post()
                        .uri(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", authUserId)
                        .header("X-Internal-Service-Id", serviceId)
                        .header("X-Internal-Key-Id", "v1")
                        .header("X-Internal-Timestamp",
                                String.valueOf(timestamp))
                        .header("X-Internal-Nonce", nonce)
                        .header("X-Internal-Signature", signature)
                        .header("X-User-Id", authUserId)
                        .header("X-User-Role", "ROLE_CUSTOMER")
                        .body(bodyBytes)
                        .retrieve()
                        .toBodilessEntity();

                log.info(
                        "Customer Service accepted profile creation request " +
                                "for authUserId={}",
                        authUserId
                );

                // Confirm that the profile really exists.
                return verifyCreatedProfile(authUserId);

            } catch (HttpClientErrorException.Conflict e) {

                // HTTP 409 is not automatically a successful operation.
                log.warn(
                        "Customer Service returned HTTP 409 for authUserId={}. " +
                                "Checking whether the profile already exists. Response: {}",
                        authUserId,
                        e.getResponseBodyAsString()
                );

                try {
                    return verifyProfileExists(authUserId);
                } catch (Exception probeException) {
                    log.error(
                            "Unable to verify profile after HTTP 409 " +
                                    "for authUserId={}",
                            authUserId,
                            probeException
                    );
                    return false;
                }

            } catch (HttpClientErrorException e) {

                // 4xx errors are not automatically retried.
                log.error(
                        "Customer Service rejected profile creation. " +
                                "HTTP status={}, authUserId={}, response={}",
                        e.getStatusCode(),
                        authUserId,
                        e.getResponseBodyAsString()
                );

                return false;

            } catch (HttpServerErrorException |
                     ResourceAccessException e) {

                log.warn(
                        "Temporary Customer Service failure. " +
                                "Attempt {}/{}, authUserId={}, error={}",
                        attempt,
                        maxRetries,
                        authUserId,
                        e.getMessage()
                );

                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(backoffMs);
                    } catch (InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();

                        log.error(
                                "Customer profile creation interrupted " +
                                        "for authUserId={}",
                                authUserId
                        );

                        return false;
                    }

                    backoffMs *= 2;
                }
            }
        }

        // All creation attempts failed. Check whether the request
        // nevertheless created the profile.
        log.warn(
                "Customer profile creation retries exhausted. " +
                        "Checking profile state for authUserId={}",
                authUserId
        );

        try {
            return verifyProfileExists(authUserId);
        } catch (Exception e) {
            log.error(
                    "Final customer profile verification failed " +
                            "for authUserId={}",
                    authUserId,
                    e
            );
            return false;
        }
    }

    private boolean verifyCreatedProfile(String authUserId) {

        try {
            boolean exists = verifyProfileExists(authUserId);

            if (exists) {
                log.info(
                        "Customer profile successfully verified " +
                                "for authUserId={}",
                        authUserId
                );
            } else {
                log.error(
                        "Customer Service accepted creation request, " +
                                "but profile was not found for authUserId={}",
                        authUserId
                );
            }

            return exists;

        } catch (Exception e) {
            log.error(
                    "Profile verification failed after creation " +
                            "for authUserId={}",
                    authUserId,
                    e
            );
            return false;
        }
    }

    @Override
    public boolean verifyProfileExists(String authUserId) {

        String path =
                "/api/customers/internal/by-auth-user/" + authUserId;

        long timestamp = System.currentTimeMillis();
        String nonce = UUID.randomUUID().toString();

        String bodyHash =
                signatureUtil.calculateBodyHash(new byte[0]);

        String canonicalString =
                signatureUtil.buildCanonicalString(
                        "GET",
                        path,
                        timestamp,
                        nonce,
                        bodyHash
                );

        String signature =
                signatureUtil.calculateSignature(
                        canonicalString,
                        internalSecret
                );

        try {
            restClient.get()
                    .uri(path)
                    .header("X-Internal-Service-Id", serviceId)
                    .header("X-Internal-Key-Id", "v1")
                    .header("X-Internal-Timestamp",
                            String.valueOf(timestamp))
                    .header("X-Internal-Nonce", nonce)
                    .header("X-Internal-Signature", signature)
                    .retrieve()
                    .toBodilessEntity();

            log.info(
                    "Customer profile exists for authUserId={}",
                    authUserId
            );

            return true;

        } catch (HttpClientErrorException.NotFound e) {

            log.warn(
                    "Customer profile NOT FOUND for authUserId={}",
                    authUserId
            );

            return false;

        } catch (Exception e) {

            log.error(
                    "Customer profile verification request failed " +
                            "for authUserId={}",
                    authUserId,
                    e
            );

            throw new IllegalStateException(
                    "Unable to verify customer profile in Customer Service",
                    e
            );
        }
    }

    @Override
    public void compensateCustomerProfile(String authUserId) {

        String path =
                "/api/customers/internal/by-auth-user/" + authUserId;

        long timestamp = System.currentTimeMillis();
        String nonce = UUID.randomUUID().toString();

        String bodyHash =
                signatureUtil.calculateBodyHash(new byte[0]);

        String canonicalString =
                signatureUtil.buildCanonicalString(
                        "DELETE",
                        path,
                        timestamp,
                        nonce,
                        bodyHash
                );

        String signature =
                signatureUtil.calculateSignature(
                        canonicalString,
                        internalSecret
                );

        try {
            log.info(
                    "Sending customer profile compensation request " +
                            "for authUserId={}",
                    authUserId
            );

            restClient.delete()
                    .uri(path)
                    .header("X-Internal-Service-Id", serviceId)
                    .header("X-Internal-Key-Id", "v1")
                    .header("X-Internal-Timestamp",
                            String.valueOf(timestamp))
                    .header("X-Internal-Nonce", nonce)
                    .header("X-Internal-Signature", signature)
                    .retrieve()
                    .toBodilessEntity();

            log.info(
                    "Customer profile compensation completed " +
                            "for authUserId={}",
                    authUserId
            );

        } catch (Exception e) {
            log.error(
                    "Customer profile compensation failed " +
                            "for authUserId={}",
                    authUserId,
                    e
            );
        }
    }
}
