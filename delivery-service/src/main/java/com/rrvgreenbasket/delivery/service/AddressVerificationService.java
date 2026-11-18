package com.rrvgreenbasket.delivery.service;

import com.rrvgreenbasket.delivery.config.DeliveryProperties;
import com.rrvgreenbasket.delivery.dto.response.ResolvedLocationResponse;
import com.rrvgreenbasket.delivery.dto.response.VerifiedAddressResponse;
import com.rrvgreenbasket.delivery.exception.AddressVerificationException;
import com.rrvgreenbasket.delivery.exception.CountryNotSupportedException;
import com.rrvgreenbasket.delivery.exception.InvalidCoordinatesException;
import com.rrvgreenbasket.delivery.exception.InvalidPlaceIdException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class AddressVerificationService {

    private final GooglePlacesService googlePlacesService;
    private final GoogleGeocodingService googleGeocodingService;
    private final DeliveryProperties deliveryProperties;

    /**
     * Verifies a customer-selected Google Place ID using Google Places API (New).
     * Validates Place ID, retrieves official coordinates, and checks country support.
     *
     * @param placeId Google Place ID
     * @return VerifiedAddressResponse with official coordinates and components
     */
    public VerifiedAddressResponse verifyPlaceId(String placeId) {
        if (!StringUtils.hasText(placeId)) {
            throw new InvalidPlaceIdException("Google Place ID is mandatory for address verification");
        }

        log.debug("Verifying address for placeId: {}", placeId);
        VerifiedAddressResponse response = googlePlacesService.getPlaceDetails(placeId.trim());

        // Validate country restriction (India only initially)
        validateCountry(response.getCountry(), response.getCountryCode());

        // Validate coordinate sanity
        validateCoordinates(response.getLatitude(), response.getLongitude());

        log.info("Address verified successfully for placeId '{}': lat={}, lng={}, formattedAddress='{}'",
                placeId, response.getLatitude(), response.getLongitude(), response.getFormattedAddress());

        return response;
    }

    /**
     * Resolves customer current GPS coordinates into a normalized address via Google Geocoding API.
     *
     * @param latitude customer device latitude
     * @param longitude customer device longitude
     * @return ResolvedLocationResponse with verified address components
     */
    public ResolvedLocationResponse resolveCurrentLocation(Double latitude, Double longitude) {
        validateCoordinates(latitude, longitude);

        log.debug("Resolving current location coordinates: lat={}, lng={}", latitude, longitude);
        ResolvedLocationResponse response = googleGeocodingService.reverseGeocode(latitude, longitude);

        // Validate country restriction
        validateCountry(response.getCountry(), null);

        log.info("Current location resolved: [lat={}, lng={}] -> address='{}', city='{}', pin='{}'",
                latitude, longitude, response.getFormattedAddress(), response.getCity(), response.getPostalCode());

        return response;
    }

    private void validateCountry(String countryName, String countryCode) {
        String allowedCountry = deliveryProperties.getDefaultCountry();
        String allowedCode = deliveryProperties.getDefaultCountryCode();

        boolean matchesCountry = countryName != null && countryName.toLowerCase().contains(allowedCountry.toLowerCase());
        boolean matchesCode = countryCode != null && countryCode.equalsIgnoreCase(allowedCode);

        if (!matchesCountry && !matchesCode) {
            log.warn("Address rejected: Country '{}' / code '{}' does not match allowed delivery country '{}' ({})",
                    countryName, countryCode, allowedCountry, allowedCode);
            throw new CountryNotSupportedException(
                    "Delivery is currently supported only in " + allowedCountry + ". Detected country: " +
                    (countryName != null ? countryName : "Unknown"));
        }
    }

    private void validateCoordinates(Double latitude, Double longitude) {
        if (latitude == null || latitude.isNaN() || latitude.isInfinite() || latitude < -90.0 || latitude > 90.0) {
            throw new InvalidCoordinatesException("Invalid latitude: " + latitude + ". Must be between -90.0 and 90.0");
        }
        if (longitude == null || longitude.isNaN() || longitude.isInfinite() || longitude < -180.0 || longitude > 180.0) {
            throw new InvalidCoordinatesException("Invalid longitude: " + longitude + ". Must be between -180.0 and 180.0");
        }
    }
}
