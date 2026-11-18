package com.rrvgreenbasket.delivery.geocoding;

import com.rrvgreenbasket.delivery.dto.response.ResolvedLocationResponse;
import com.rrvgreenbasket.delivery.service.GoogleGeocodingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Google Maps implementation of GeocodingProvider, replacing legacy Nominatim.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "google.maps.geocoding-enabled", havingValue = "true", matchIfMissing = true)
public class GoogleGeocodingProvider implements GeocodingProvider {

    private final GoogleGeocodingService googleGeocodingService;

    public GoogleGeocodingProvider(GoogleGeocodingService googleGeocodingService) {
        this.googleGeocodingService = googleGeocodingService;
    }

    @Override
    public String getProviderName() {
        return "GOOGLE_MAPS";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public GeocodingResult reverseGeocode(double latitude, double longitude) {
        try {
            ResolvedLocationResponse response = googleGeocodingService.reverseGeocode(latitude, longitude);
            return GeocodingResult.builder()
                    .formattedAddress(response.getFormattedAddress())
                    .city(response.getCity())
                    .state(response.getState())
                    .pincode(response.getPostalCode())
                    .success(true)
                    .message("Google reverse geocoding resolved successfully")
                    .providerName("GOOGLE_MAPS")
                    .build();
        } catch (Exception e) {
            log.warn("Google reverse geocoding error: {}", e.getMessage());
            return GeocodingResult.failure("GOOGLE_MAPS", e.getMessage());
        }
    }
}
