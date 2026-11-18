package com.rrvgreenbasket.cart.security;

/**
 * ReplayProtectionService
 *
 * <p><strong>What it does:</strong></p>
 * Abstraction for tracking cryptographic assertion nonces / JWT IDs (jti)
 * to prevent replay attacks across single and multi-instance deployments.
 * <p>
 * If a distributed cache like Redis is unavailable, the in-memory implementation
 * provides thread-safe single-node protection with automated TTL eviction.
 */
public interface ReplayProtectionService {

    /**
     * Checks if the given assertion ID / nonce has been observed.
     * If not observed, atomically records it with the specified TTL in milliseconds and returns true.
     * If already observed within the active TTL window, returns false (replay detected).
     *
     * @param nonce Nonce or jti claim
     * @param ttlMs Time-to-live in milliseconds
     * @return true if nonce is accepted and recorded, false if replayed or invalid
     */
    boolean checkAndStoreNonce(String nonce, long ttlMs);

    default boolean checkAndStoreNonce(String nonce) {
        return checkAndStoreNonce(nonce, 30_000L);
    }

    void clear();
}
