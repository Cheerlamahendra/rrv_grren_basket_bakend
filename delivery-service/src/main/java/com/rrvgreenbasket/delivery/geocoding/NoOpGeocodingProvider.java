package com.rrvgreenbasket.delivery.geocoding;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "google.maps.geocoding-enabled", havingValue = "false")
public class NoOpGeocodingProvider implements GeocodingProvider {

    @Override
    public String getProviderName() {
        return "NOOP";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public GeocodingResult reverseGeocode(double latitude, double longitude) {
        return GeocodingResult.empty("NOOP");
    }
}
