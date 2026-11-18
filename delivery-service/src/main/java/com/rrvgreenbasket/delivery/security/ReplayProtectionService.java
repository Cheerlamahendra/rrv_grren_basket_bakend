package com.rrvgreenbasket.delivery.security;

public interface ReplayProtectionService {

    /**
     * Checks if nonce has been seen.
     *
     * @param nonce unique identifier of the assertion
     * @param ttlMs duration to remember the nonce
     * @return true if nonce is fresh (stored successfully), false if nonce already exists (replayed)
     */
    boolean checkAndStoreNonce(String nonce, long ttlMs);
}
