package com.rrvgreenbasket.delivery.geocoding;

public interface GeocodingProvider {

    String getProviderName();

    boolean isAvailable();

    GeocodingResult reverseGeocode(double latitude, double longitude);
}
