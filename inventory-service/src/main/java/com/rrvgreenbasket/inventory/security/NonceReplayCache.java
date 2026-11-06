package com.rrvgreenbasket.inventory.security;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class NonceReplayCache {

    private static final long NONCE_VALIDITY_SECONDS = 300; // 5 minutes
    private final Map<String, Instant> cache = new ConcurrentHashMap<>();

    public synchronized boolean checkAndStoreNonce(String nonce) {
        cleanExpiredNonces();
        if (nonce == null || nonce.isBlank()) {
            return false;
        }
        if (cache.containsKey(nonce)) {
            return false; // Replayed nonce
        }
        cache.put(nonce, Instant.now().plusSeconds(NONCE_VALIDITY_SECONDS));
        return true;
    }

    private void cleanExpiredNonces() {
        Instant now = Instant.now();
        cache.entrySet().removeIf(entry -> entry.getValue().isBefore(now));
    }
}
