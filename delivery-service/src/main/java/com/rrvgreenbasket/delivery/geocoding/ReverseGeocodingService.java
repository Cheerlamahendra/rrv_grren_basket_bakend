package com.rrvgreenbasket.delivery.geocoding;

import com.rrvgreenbasket.delivery.util.AddressNormalizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ReverseGeocodingService {

    private final GeocodingProvider geocodingProvider;
    private final boolean enabled;
    private final boolean enforceConsistency;

    public ReverseGeocodingService(
            GeocodingProvider geocodingProvider,
            @Value("${app.geocoding.enabled:false}") boolean enabled,
            @Value("${app.geocoding.enforce-consistency:false}") boolean enforceConsistency) {
        this.geocodingProvider = geocodingProvider;
        this.enabled = enabled;
        this.enforceConsistency = enforceConsistency;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Checks address consistency between supplied textual address and reverse-geocoded coordinates.
     * Non-blocking for legacy requests or when geocoding is disabled.
     *
     * @param latitude customer latitude
     * @param longitude customer longitude
     * @param city customer provided city
     * @param state customer provided state
     * @param pincode customer provided pincode
     * @return consistency verification message or null if fully consistent / bypassed
     */
    public String verifyAddressConsistency(double latitude, double longitude, String city, String state, String pincode) {
        if (!enabled || !geocodingProvider.isAvailable()) {
            return null;
        }

        GeocodingResult result = geocodingProvider.reverseGeocode(latitude, longitude);
        if (!result.success() || result.formattedAddress() == null) {
            log.debug("Reverse geocoding not conclusive for [lat={}, lng={}]: {}", latitude, longitude, result.message());
            return null;
        }

        // Compare city if both available
        if (city != null && result.city() != null && !AddressNormalizer.matches(city, result.city())) {
            log.warn("Address consistency warning: Customer city '{}' differs from geocoded city '{}'", city, result.city());
            if (enforceConsistency) {
                return "Location coordinates correspond to " + result.city() + " rather than " + city;
            }
        }

        // Compare state if both available
        if (state != null && result.state() != null && !AddressNormalizer.matches(state, result.state())) {
            log.warn("Address consistency warning: Customer state '{}' differs from geocoded state '{}'", state, result.state());
            if (enforceConsistency) {
                return "Location coordinates correspond to " + result.state() + " rather than " + state;
            }
        }

        return null;
    }
}
