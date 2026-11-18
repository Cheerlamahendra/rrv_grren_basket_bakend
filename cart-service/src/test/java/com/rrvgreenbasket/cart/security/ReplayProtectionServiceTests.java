package com.rrvgreenbasket.cart.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ReplayProtectionService Tests")
class ReplayProtectionServiceTests {

    private InMemoryReplayProtectionService replayService;

    @BeforeEach
    void setUp() {
        replayService = new InMemoryReplayProtectionService();
    }

    @Test
    @DisplayName("Accepts new nonce and rejects replay within TTL")
    void testAcceptsAndRejectsReplay() {
        String nonce = "test-nonce-123";

        // First attempt -> accepted
        boolean first = replayService.checkAndStoreNonce(nonce, 10_000L);
        assertTrue(first, "First nonce presentation must be accepted");

        // Immediate second attempt -> rejected as replay
        boolean second = replayService.checkAndStoreNonce(nonce, 10_000L);
        assertFalse(second, "Second presentation within TTL must be rejected");
    }

    @Test
    @DisplayName("Rejects null or blank nonce")
    void testRejectsNullOrBlankNonce() {
        assertFalse(replayService.checkAndStoreNonce(null, 10_000L));
        assertFalse(replayService.checkAndStoreNonce("", 10_000L));
        assertFalse(replayService.checkAndStoreNonce("   ", 10_000L));
    }

    @Test
    @DisplayName("Clearing storage resets accepted nonces")
    void testClearStorage() {
        String nonce = "clearable-nonce";
        assertTrue(replayService.checkAndStoreNonce(nonce, 10_000L));
        replayService.clear();
        assertTrue(replayService.checkAndStoreNonce(nonce, 10_000L), "After clear, nonce should be accepted again");
    }
}
