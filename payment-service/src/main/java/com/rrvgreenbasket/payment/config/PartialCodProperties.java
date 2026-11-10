package com.rrvgreenbasket.payment.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "payment.partial-cod")
public class PartialCodProperties {

    private boolean enabled = true;
    private int advancePercentage = 50;
    private int deliveryCollectionPercentage = 50;
    private String currency = "INR";

    @PostConstruct
    public void validatePercentages() {
        if (enabled) {
            if (advancePercentage <= 0 || advancePercentage >= 100) {
                throw new IllegalStateException("Advance percentage must be between 1 and 99, but was " + advancePercentage);
            }
            if (advancePercentage + deliveryCollectionPercentage != 100) {
                throw new IllegalStateException(
                        String.format("Payment percentage configuration invalid: advancePercentage (%d) + deliveryCollectionPercentage (%d) must total exactly 100%%",
                                advancePercentage, deliveryCollectionPercentage)
                );
            }
        }
    }
}
