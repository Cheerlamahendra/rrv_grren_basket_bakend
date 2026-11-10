package com.rrvgreenbasket.store.security;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

@Component
public class S2SSignatureUtil {

    private static final String HMAC_SHA256 = "HmacSHA256";

    public String calculateBodyHash(byte[] body) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(body != null ? body : new byte[0]);
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }

    public String buildCanonicalString(String method, String path, long timestamp, String nonce, String bodyHash) {
        return String.format("%s\n%s\n%d\n%s\n%s",
                method.toUpperCase(),
                path,
                timestamp,
                nonce,
                bodyHash != null ? bodyHash : ""
        );
    }

    public String calculateSignature(String canonicalString, String secretKey) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    secretKey.getBytes(StandardCharsets.UTF_8),
                    HMAC_SHA256
            );
            mac.init(secretKeySpec);
            byte[] rawHmac = mac.doFinal(canonicalString.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(rawHmac);
        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate HMAC signature", e);
        }
    }

    public boolean verifySignature(String canonicalString, String secretKey, String expectedSignature) {
        if (expectedSignature == null || expectedSignature.isBlank()) {
            return false;
        }
        String calculated = calculateSignature(canonicalString, secretKey);
        return MessageDigest.isEqual(
                calculated.getBytes(StandardCharsets.UTF_8),
                expectedSignature.getBytes(StandardCharsets.UTF_8)
        );
    }
}
