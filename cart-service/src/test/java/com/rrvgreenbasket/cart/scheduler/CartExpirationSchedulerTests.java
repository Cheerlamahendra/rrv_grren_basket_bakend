package com.rrvgreenbasket.cart.scheduler;

import com.rrvgreenbasket.cart.service.CartService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CartExpirationScheduler Tests")
class CartExpirationSchedulerTests {

    @Mock
    private CartService cartService;

    @InjectMocks
    private CartExpirationScheduler scheduler;

    @Test
    @DisplayName("Executes batch cart expiration cleanup successfully")
    void testRunCartExpirationCleanup_Success() {
        when(cartService.expireAbandonedCarts()).thenReturn(5);

        scheduler.runCartExpirationCleanup();

        verify(cartService, times(1)).expireAbandonedCarts();
    }

    @Test
    @DisplayName("Handles exceptions during cleanup gracefully without bubbling up")
    void testRunCartExpirationCleanup_HandlesException() {
        when(cartService.expireAbandonedCarts()).thenThrow(new RuntimeException("DB Connection Timeout"));

        // Should not throw
        scheduler.runCartExpirationCleanup();

        verify(cartService, times(1)).expireAbandonedCarts();
    }
}
