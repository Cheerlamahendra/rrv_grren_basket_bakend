package com.rrvgreenbasket.order.security;

public interface ReplayProtectionService {
    boolean checkAndStoreNonce(String nonce, long ttlMs);
}
