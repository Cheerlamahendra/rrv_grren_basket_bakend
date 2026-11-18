package com.rrvgreenbasket.cart.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "cart.expiration")
public class CartExpirationProperties {

    /**
     * Duration in days after which an inactive cart expires. Default: 7 days.
     */
    private int durationDays = 7;

    /**
     * Scheduler execution interval in milliseconds. Default: 3,600,000 ms (1 hour).
     */
    private long cleanupIntervalMs = 3600000L;

    /**
     * Batch size for paginated expiration processing. Default: 100.
     */
    private int batchSize = 100;
}
