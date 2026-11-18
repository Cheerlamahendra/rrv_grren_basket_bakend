package com.rrvgreenbasket.delivery.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class InMemoryReplayProtectionService implements ReplayProtectionService {

    private final Map<String, Long> nonceCache = new ConcurrentHashMap<>();

    @Override
    public boolean checkAndStoreNonce(String nonce, long ttlMs) {
        if (nonce == null || nonce.isBlank()) {
            return false;
        }

        long now = System.currentTimeMillis();
        long expiry = now + (ttlMs > 0 ? ttlMs : 30_000L);

        Long previousExpiry = nonceCache.putIfAbsent(nonce, expiry);
        if (previousExpiry != null && previousExpiry > now) {
            log.warn("Replay attack detected for nonce: {}", nonce);
            return false;
        }

        nonceCache.put(nonce, expiry);
        return true;
    }

    @Scheduled(fixedRate = 60000)
    public void evictExpiredNonces() {
        long now = System.currentTimeMillis();
        nonceCache.entrySet().removeIf(entry -> entry.getValue() <= now);
    }
}
