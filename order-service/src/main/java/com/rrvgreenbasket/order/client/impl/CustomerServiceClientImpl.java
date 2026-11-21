package com.rrvgreenbasket.order.client.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrvgreenbasket.order.client.CustomerServiceClient;
import com.rrvgreenbasket.order.client.dto.AddressClientResponse;
import com.rrvgreenbasket.order.client.dto.CustomerClientResponse;
import com.rrvgreenbasket.order.dto.request.AddressDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class CustomerServiceClientImpl implements CustomerServiceClient {

    private static final String HMAC_SHA256_ALGORITHM = "HmacSHA256";

    private final RestClient restClient;
    private final String internalSecret;
    private final String serviceId;
    private final ObjectMapper objectMapper;

    @Autowired
    public CustomerServiceClientImpl(
            @Value("${app.services.customer-service-url:http://localhost:8082}") String customerServiceUrl,
            @Value("${app.security.internal-service-secret:RQpmB5hGO2NsLNE7OnwXo5NfNtEJj+j+461g0oEeGunJPZ4iYQEGr+uk5M3hAfgDg26IDtmA9N2RJbFXkCLrqQ==}") String internalSecret,
            @Value("${app.security.internal-service-id:order-service}") String serviceId,
            @Value("${app.client.connect-timeout-ms:3000}") int connectTimeout,
            @Value("${app.client.read-timeout-ms:5000}") int readTimeout,
            @Autowired(required = false) ObjectMapper objectMapper) {

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);

        this.restClient = RestClient.builder()
                .baseUrl(customerServiceUrl.replaceAll("/+$", ""))
                .requestFactory(factory)
                .build();

        this.internalSecret = internalSecret;
        this.serviceId = serviceId;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    public CustomerServiceClientImpl(RestClient restClient, String internalSecret, String serviceId, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.internalSecret = internalSecret;
        this.serviceId = serviceId;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    @Override
    public CustomerClientResponse getCustomerProfile(String customerIdentifier) {
        if (!StringUtils.hasText(customerIdentifier)) {
            return null;
        }
        String path = "/api/customers/internal/profile/" + customerIdentifier.trim();
        log.debug("Calling Customer Service profile lookup for '{}'", customerIdentifier);
        try {
            Map<String, String> headers = buildS2SHeaders("GET", path, new byte[0]);

            String rawJson = restClient.get()
                    .uri(path)
                    .headers(httpHeaders -> headers.forEach(httpHeaders::set))
                    .retrieve()
                    .body(String.class);

            if (!StringUtils.hasText(rawJson)) {
                return null;
            }

            JsonNode root = objectMapper.readTree(rawJson);
            if (root.has("data") && !root.get("data").isNull()) {
                return objectMapper.treeToValue(root.get("data"), CustomerClientResponse.class);
            }
            return objectMapper.treeToValue(root, CustomerClientResponse.class);
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("Customer profile not found in customer-service for identifier: {}", customerIdentifier);
            return null;
        } catch (Exception e) {
            log.warn("Unable to fetch customer profile from customer-service for '{}': {}", customerIdentifier, e.getMessage());
            return null;
        }
    }

    @Override
    public AddressClientResponse getAddress(String customerIdentifier, String addressPublicId) {
        if (!StringUtils.hasText(addressPublicId)) {
            return null;
        }
        String effectiveCustomer = StringUtils.hasText(customerIdentifier) ? customerIdentifier.trim() : "";
        String pathAndQuery = "/api/customers/internal/addresses/" + addressPublicId.trim() + "?customerId=" + effectiveCustomer;
        log.debug("Calling Customer Service getAddress: addressId='{}', customerId='{}'", addressPublicId, effectiveCustomer);
        try {
            Map<String, String> headers = buildS2SHeaders("GET", pathAndQuery, new byte[0]);

            String rawJson = restClient.get()
                    .uri(pathAndQuery)
                    .headers(httpHeaders -> headers.forEach(httpHeaders::set))
                    .retrieve()
                    .body(String.class);

            if (!StringUtils.hasText(rawJson)) {
                return null;
            }

            JsonNode root = objectMapper.readTree(rawJson);
            if (root.has("data") && !root.get("data").isNull()) {
                return objectMapper.treeToValue(root.get("data"), AddressClientResponse.class);
            }
            return objectMapper.treeToValue(root, AddressClientResponse.class);
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("Address '{}' not found for customer '{}' in customer-service", addressPublicId, effectiveCustomer);
            return null;
        } catch (Exception e) {
            log.warn("Customer Service getAddress call failed: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public AddressClientResponse saveAddress(String customerIdentifier, AddressDto addressDto) {
        if (addressDto == null) {
            return null;
        }
        String effectiveCustomer = StringUtils.hasText(customerIdentifier) ? customerIdentifier.trim() : "";
        String pathAndQuery = "/api/customers/internal/addresses?customerId=" + effectiveCustomer;
        log.info("Calling Customer Service saveAddress for customer '{}', receiver '{}'",
                effectiveCustomer, addressDto.getRecipientName());
        try {
            Map<String, Object> bodyMap = new HashMap<>();
            bodyMap.put("receiverName", addressDto.getRecipientName() != null ? addressDto.getRecipientName() : "Receiver");
            bodyMap.put("receiverMobile", addressDto.getRecipientMobile() != null ? addressDto.getRecipientMobile() : "9999999999");
            bodyMap.put("addressLine1", addressDto.getAddressLine1() != null ? addressDto.getAddressLine1() : addressDto.getAddressLine());
            bodyMap.put("addressLine2", addressDto.getAddressLine2());
            bodyMap.put("landmark", addressDto.getLandmark());
            bodyMap.put("city", addressDto.getCity());
            bodyMap.put("state", addressDto.getState());
            bodyMap.put("pincode", addressDto.getPincode());
            bodyMap.put("country", addressDto.getCountry() != null ? addressDto.getCountry() : "India");
            bodyMap.put("addressType", "HOME");
            bodyMap.put("isDefault", false);

            byte[] bodyBytes = objectMapper.writeValueAsBytes(bodyMap);
            Map<String, String> headers = buildS2SHeaders("POST", pathAndQuery, bodyBytes);

            String rawJson = restClient.post()
                    .uri(pathAndQuery)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(httpHeaders -> headers.forEach(httpHeaders::set))
                    .body(bodyBytes)
                    .retrieve()
                    .body(String.class);

            if (!StringUtils.hasText(rawJson)) {
                return null;
            }

            JsonNode root = objectMapper.readTree(rawJson);
            if (root.has("data") && !root.get("data").isNull()) {
                return objectMapper.treeToValue(root.get("data"), AddressClientResponse.class);
            }
            return objectMapper.treeToValue(root, AddressClientResponse.class);
        } catch (Exception e) {
            log.warn("Customer Service saveAddress call failed: {}", e.getMessage());
            return null;
        }
    }

    private Map<String, String> buildS2SHeaders(String method, String pathAndQuery, byte[] bodyBytes) {
        long timestamp = System.currentTimeMillis();
        String nonce = UUID.randomUUID().toString();
        String bodyHash = calculateBodyHash(bodyBytes);
        String canonicalString = (method != null ? method.toUpperCase() : "") + "\n" +
                (pathAndQuery != null ? pathAndQuery : "") + "\n" +
                timestamp + "\n" +
                nonce + "\n" +
                bodyHash;

        String signature = calculateHmacSha256(canonicalString, internalSecret);

        Map<String, String> headers = new HashMap<>();
        headers.put("X-Internal-Service-Id", serviceId);
        headers.put("X-Internal-Timestamp", String.valueOf(timestamp));
        headers.put("X-Internal-Nonce", nonce);
        headers.put("X-Internal-Signature", signature);
        return headers;
    }

    private String calculateBodyHash(byte[] bodyBytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(bodyBytes != null ? bodyBytes : new byte[0]);
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private String calculateHmacSha256(String data, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256_ALGORITHM);
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256_ALGORITHM);
            mac.init(secretKeySpec);
            byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(rawHmac);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute HMAC-SHA256 signature", e);
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
