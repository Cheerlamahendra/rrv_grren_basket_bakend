package com.rrvgreenbasket.cart.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * InMemoryReplayProtectionService
 *
 * <p><strong>What it does:</strong></p>
 * Sliding-window thread-safe in-memory cache for Gateway assertion nonces.
 * Evicts expired nonces and rejects any duplicated assertion IDs within the TTL window.
 * <p>
 * <em>Deployment note:</em> For multi-instance clustered deployments, a Redis-backed
 * implementation of {@link ReplayProtectionService} should be registered.
 */
@Slf4j
@Service
@ConditionalOnMissingBean(name = "redisReplayProtectionService")
public class InMemoryReplayProtectionService implements ReplayProtectionService {

    private final ConcurrentHashMap<String, Long> nonceMap = new ConcurrentHashMap<>();

    @Override
    public synchronized boolean checkAndStoreNonce(String nonce, long ttlMs) {
        long now = System.currentTimeMillis();
        evictExpired(now);

        if (nonce == null || nonce.isBlank()) {
            return false;
        }

        long newExpiry = now + ttlMs;
        Long existingExpiry = nonceMap.get(nonce);

        if (existingExpiry != null && existingExpiry > now) {
            log.warn("Replay attack detected for nonce: {}", nonce);
            return false; // Replay!
        }

        nonceMap.put(nonce, newExpiry);
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

    @Override
    public void clear() {
        nonceMap.clear();
    }
}
