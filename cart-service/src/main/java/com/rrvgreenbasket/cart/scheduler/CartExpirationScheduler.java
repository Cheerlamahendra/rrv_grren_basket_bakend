package com.rrvgreenbasket.cart.scheduler;

import com.rrvgreenbasket.cart.service.CartService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * CartExpirationScheduler
 *
 * <p><strong>What it does:</strong></p>
 * Periodically identifies and transitions abandoned/expired customer carts to EXPIRED status in batches.
 * Configured via Config Server properties (cart.expiration.*).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CartExpirationScheduler {

    private final CartService cartService;

    @Scheduled(
            fixedDelayString = "${cart.expiration.cleanup-interval-ms:3600000}",
            initialDelayString = "${cart.expiration.initial-delay-ms:60000}"
    )
    public void runCartExpirationCleanup() {
        log.info("Starting scheduled cart expiration cleanup task...");
        try {
            int expiredCount = cartService.expireAbandonedCarts();
            if (expiredCount > 0) {
                log.info("Cart expiration run completed. Successfully transitioned {} abandoned carts to EXPIRED.", expiredCount);
            } else {
                log.debug("Cart expiration run completed. No expired carts found.");
            }
        } catch (Exception ex) {
            log.error("Error during scheduled cart expiration task: {}", ex.getMessage(), ex);
        }
    }
}
