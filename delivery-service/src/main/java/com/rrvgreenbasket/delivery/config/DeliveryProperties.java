package com.rrvgreenbasket.delivery.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "delivery")
public class DeliveryProperties {

    /**
     * Default operating currency (e.g. INR).
     */
    private String defaultCurrency = "INR";

    /**
     * Default operating country name (e.g. India).
     */
    private String defaultCountry = "India";

    /**
     * Default operating 2-letter ISO country code (e.g. IN).
     */
    private String defaultCountryCode = "IN";

    /**
     * Default maximum road delivery distance in meters (e.g. 5000 = 5 km).
     */
    private int defaultMaximumDistanceMeters = 5000;
}
