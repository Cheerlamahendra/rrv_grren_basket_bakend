package com.rrvgreenbasket.review.security;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class NonceReplayCache {

    private final ConcurrentHashMap<String, Instant> nonceMap = new ConcurrentHashMap<>();
    private static final long TTL_SECONDS = 300; // 5 minutes

    public boolean checkAndStoreNonce(String nonce) {
        if (nonce == null || nonce.isBlank()) {
            return false;
        }

        cleanupExpired();

        Instant now = Instant.now();
        Instant existing = nonceMap.putIfAbsent(nonce, now);
        return existing == null;
    }

    private void cleanupExpired() {
        Instant expiryThreshold = Instant.now().minusSeconds(TTL_SECONDS);
        nonceMap.entrySet().removeIf(entry -> entry.getValue().isBefore(expiryThreshold));
    }
}
