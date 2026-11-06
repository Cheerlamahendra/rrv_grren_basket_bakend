package com.rrvgreenbasket.gateway.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * S2SSignatureUtil
 *
 * <p><strong>What it does:</strong></p>
 * Implements HMAC-SHA256 canonical request signing for API Gateway's internal calls to microservices.
 */
@Component
public class S2SSignatureUtil {

    private static final Logger log = LoggerFactory.getLogger(S2SSignatureUtil.class);
    private static final String HMAC_SHA256_ALGORITHM = "HmacSHA256";

    public String calculateBodyHash(byte[] bodyBytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(bodyBytes != null ? bodyBytes : new byte[0]);
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    public String buildCanonicalString(String method, String pathAndQuery, long timestamp, String nonce, String bodyHash) {
        return (method != null ? method.toUpperCase() : "") + "\n" +
                (pathAndQuery != null ? pathAndQuery : "") + "\n" +
                timestamp + "\n" +
                (nonce != null ? nonce : "") + "\n" +
                (bodyHash != null ? bodyHash : "");
    }

    public String calculateSignature(String canonicalString, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256_ALGORITHM);
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256_ALGORITHM);
            mac.init(secretKeySpec);
            byte[] rawHmac = mac.doFinal(canonicalString.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(rawHmac);
        } catch (Exception e) {
            log.error("Failed to compute HMAC-SHA256 signature in Gateway: {}", e.getMessage());
            throw new IllegalStateException("Failed to calculate S2S signature", e);
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
