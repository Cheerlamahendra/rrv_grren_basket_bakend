package com.rrvgreenbasket.delivery.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "google.maps")
public class GoogleMapsProperties {

    /**
     * Google Maps Server API Key (Places New, Geocoding, Routes APIs).
     * Must be kept secure and never exposed to clients.
     */
    private String serverApiKey;

    /**
     * Google Maps Browser API Key (Maps JavaScript API).
     * Used by Next.js frontend with website HTTP referrer restrictions.
     */
    private String browserApiKey;

    /**
     * Whether Google Places API (New) is enabled.
     */
    private boolean placesEnabled = true;

    /**
     * Whether Google Geocoding API is enabled.
     */
    private boolean geocodingEnabled = true;

    /**
     * Whether Google Routes API is enabled.
     */
    private boolean routesEnabled = true;

    /**
     * HTTP client connect timeout in milliseconds.
     */
    private int connectTimeoutMs = 5000;

    /**
     * HTTP client read timeout in milliseconds.
     */
    private int readTimeoutMs = 10000;

    /**
     * Base URL for Google Places API (New).
     */
    private String placesBaseUrl = "https://places.googleapis.com";

    /**
     * Base URL for Google Geocoding API.
     */
    private String geocodingBaseUrl = "https://maps.googleapis.com";

    /**
     * Base URL for Google Routes API.
     */
    private String routesBaseUrl = "https://routes.googleapis.com";
}
