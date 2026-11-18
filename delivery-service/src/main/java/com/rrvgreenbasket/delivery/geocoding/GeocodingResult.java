package com.rrvgreenbasket.delivery.geocoding;

import lombok.Builder;

@Builder
public record GeocodingResult(
        String formattedAddress,
        String city,
        String state,
        String pincode,
        boolean success,
        String message,
        String providerName
) {
    public static GeocodingResult empty(String providerName) {
        return new GeocodingResult(null, null, null, null, true, "Geocoding bypassed or not required", providerName);
    }

    public static GeocodingResult failure(String providerName, String message) {
        return new GeocodingResult(null, null, null, null, false, message, providerName);
    }
}
