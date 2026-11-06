package com.rrvgreenbasket.customer.security;

import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * NonceReplayCache
 *
 * <p><strong>What it does:</strong></p>
 * Sliding nonce cache preventing request replay attacks within the 30-second skew window.
 */
@Component
public class NonceReplayCache {

    private static final long DEFAULT_TTL_MS = 30_000L;
    private final ConcurrentHashMap<String, Long> nonceMap = new ConcurrentHashMap<>();

    public synchronized boolean checkAndStoreNonce(String nonce) {
        return checkAndStoreNonce(nonce, DEFAULT_TTL_MS);
    }

    public synchronized boolean checkAndStoreNonce(String nonce, long ttlMs) {
        long now = System.currentTimeMillis();
        evictExpired(now);

        if (nonce == null || nonce.isBlank()) {
            return false;
        }

        Long existingExpiry = nonceMap.get(nonce);
        if (existingExpiry != null && existingExpiry > now) {
            return false; // Replay detected!
        }

        nonceMap.put(nonce, now + ttlMs);
        return true;
    }

    private void evictExpired(long now) {
        Iterator<Map.Entry<String, Long>> it = nonceMap.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue() <= now) {
                it.remove();
            }
        }
    }

    public void clear() {
        nonceMap.clear();
    }
}
