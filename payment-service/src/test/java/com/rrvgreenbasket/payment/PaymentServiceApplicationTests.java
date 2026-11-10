package com.rrvgreenbasket.payment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Integration Test — Payment Service Application Context Loads")
class PaymentServiceApplicationTests {

    @Test
    void contextLoads() {
        assertTrue(true, "Application context loaded successfully");
    }
}
