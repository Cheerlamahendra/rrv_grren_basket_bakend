package com.rrvgreenbasket.delivery.geocoding;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

/**
 * Legacy Nominatim provider - retired from active production in favor of Google Maps Geocoding & Places.
 * Retained solely as a deprecated legacy reference.
 */
@Slf4j
@Deprecated
public class NominatimGeocodingProvider implements GeocodingProvider {

    @Override
    public String getProviderName() {
        return "NOMINATIM_RETIRED";
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public GeocodingResult reverseGeocode(double latitude, double longitude) {
        return GeocodingResult.failure("NOMINATIM_RETIRED", "Nominatim is retired; use GoogleGeocodingProvider");
    }
}
