package com.rrvgreenbasket.delivery.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrvgreenbasket.delivery.config.GoogleMapsProperties;
import com.rrvgreenbasket.delivery.dto.response.ResolvedLocationResponse;
import com.rrvgreenbasket.delivery.dto.response.VerifiedAddressResponse;
import com.rrvgreenbasket.delivery.exception.AddressVerificationException;
import com.rrvgreenbasket.delivery.exception.GoogleApiException;
import com.rrvgreenbasket.delivery.exception.GoogleApiQuotaExceededException;
import com.rrvgreenbasket.delivery.exception.InvalidCoordinatesException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Service integrating with Google Geocoding API for reverse and forward geocoding.
 */
@Slf4j
@Service
public class GoogleGeocodingService {

    private final RestClient restClient;
    private final GoogleMapsProperties properties;
    private final ObjectMapper objectMapper;

    public GoogleGeocodingService(
            @Qualifier("googleGeocodingRestClient") RestClient restClient,
            GoogleMapsProperties properties,
            ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * Reverse-geocodes customer coordinates to resolve an address.
     *
     * @param latitude customer device latitude
     * @param longitude customer device longitude
     * @return ResolvedLocationResponse with normalized address and place ID
     */
    public ResolvedLocationResponse reverseGeocode(Double latitude, Double longitude) {
        validateCoordinates(latitude, longitude);

        String apiKey = properties.getServerApiKey();
        if (!StringUtils.hasText(apiKey)) {
            log.error("Google Maps Server API key is not configured");
            throw new GoogleApiException("Google Maps Server API key is not configured");
        }

        log.debug("Calling Google Geocoding API (reverse) for lat={}, lng={}", latitude, longitude);

        try {
            String latlngParam = String.format(java.util.Locale.US, "%f,%f", latitude, longitude);
            String responseBody = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/maps/api/geocode/json")
                            .queryParam("latlng", latlngParam)
                            .queryParam("key", apiKey.trim())
                            .build())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String.class);

            if (!StringUtils.hasText(responseBody)) {
                throw new AddressVerificationException("Empty response received from Google Geocoding API");
            }

            return parseReverseGeocodeResponse(responseBody, latitude, longitude);

        } catch (InvalidCoordinatesException | GoogleApiQuotaExceededException | AddressVerificationException | GoogleApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Reverse geocoding failed for [lat={}, lng={}]: {}", latitude, longitude, e.getMessage());
            throw new GoogleApiException("Failed to communicate with Google Geocoding API: " + e.getMessage(), e);
        }
    }

    /**
     * Forward-geocodes an address string to coordinates and place details.
     *
     * @param address physical address string
     * @return VerifiedAddressResponse containing verified coordinates and place ID
     */
    public VerifiedAddressResponse forwardGeocode(String address) {
        if (!StringUtils.hasText(address)) {
            throw new AddressVerificationException("Address cannot be null or empty for forward geocoding");
        }

        String apiKey = properties.getServerApiKey();
        if (!StringUtils.hasText(apiKey)) {
            log.error("Google Maps Server API key is not configured");
            throw new GoogleApiException("Google Maps Server API key is not configured");
        }

        log.debug("Calling Google Geocoding API (forward) for address: {}", address);

        try {
            String responseBody = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/maps/api/geocode/json")
                            .queryParam("address", address.trim())
                            .queryParam("key", apiKey.trim())
                            .build())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String.class);

            if (!StringUtils.hasText(responseBody)) {
                throw new AddressVerificationException("Empty response received from Google Geocoding API");
            }

            return parseForwardGeocodeResponse(responseBody, address);

        } catch (AddressVerificationException | GoogleApiException | GoogleApiQuotaExceededException e) {
            throw e;
        } catch (Exception e) {
            log.error("Forward geocoding failed for address '{}': {}", address, e.getMessage());
            throw new GoogleApiException("Failed to communicate with Google Geocoding API: " + e.getMessage(), e);
        }
    }

    private ResolvedLocationResponse parseReverseGeocodeResponse(String json, Double latitude, Double longitude) {
        try {
            JsonNode root = objectMapper.readTree(json);
            String status = root.path("status").asText("");

            handleGeocodingStatus(status);

            JsonNode results = root.path("results");
            if (!results.isArray() || results.isEmpty()) {
                throw new AddressVerificationException("No address found for coordinates [lat=" + latitude + ", lng=" + longitude + "]");
            }

            JsonNode bestResult = results.get(0);
            String placeId = bestResult.path("place_id").asText(null);
            String formattedAddress = bestResult.path("formatted_address").asText(null);

            String city = null;
            String state = null;
            String postalCode = null;
            String country = null;

            JsonNode components = bestResult.path("address_components");
            if (components.isArray()) {
                for (JsonNode comp : components) {
                    JsonNode types = comp.path("types");
                    String longName = comp.path("long_name").asText(null);
                    String shortName = comp.path("short_name").asText(null);

                    if (hasType(types, "postal_code")) {
                        postalCode = longName != null ? longName : shortName;
                    } else if (hasType(types, "locality")) {
                        city = longName != null ? longName : shortName;
                    } else if (city == null && hasType(types, "administrative_area_level_2")) {
                        city = longName != null ? longName : shortName;
                    } else if (hasType(types, "administrative_area_level_1")) {
                        state = longName != null ? longName : shortName;
                    } else if (hasType(types, "country")) {
                        country = longName != null ? longName : shortName;
                    }
                }
            }

            return ResolvedLocationResponse.builder()
                    .verified(true)
                    .placeId(placeId)
                    .formattedAddress(formattedAddress)
                    .latitude(latitude)
                    .longitude(longitude)
                    .city(city)
                    .state(state)
                    .postalCode(postalCode)
                    .country(country)
                    .message("Current location reverse-geocoded successfully")
                    .build();

        } catch (AddressVerificationException | GoogleApiQuotaExceededException | GoogleApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse Google Geocoding JSON response: {}", e.getMessage(), e);
            throw new AddressVerificationException("Unable to parse Google Geocoding response: " + e.getMessage(), e);
        }
    }

    private VerifiedAddressResponse parseForwardGeocodeResponse(String json, String originalAddress) {
        try {
            JsonNode root = objectMapper.readTree(json);
            String status = root.path("status").asText("");

            handleGeocodingStatus(status);

            JsonNode results = root.path("results");
            if (!results.isArray() || results.isEmpty()) {
                throw new AddressVerificationException("No location found for address: " + originalAddress);
            }

            JsonNode bestResult = results.get(0);
            String placeId = bestResult.path("place_id").asText(null);
            String formattedAddress = bestResult.path("formatted_address").asText(originalAddress);

            JsonNode location = bestResult.path("geometry").path("location");
            double lat = location.path("lat").asDouble();
            double lng = location.path("lng").asDouble();

            validateCoordinates(lat, lng);

            String city = null;
            String state = null;
            String postalCode = null;
            String country = null;
            String countryCode = null;

            JsonNode components = bestResult.path("address_components");
            if (components.isArray()) {
                for (JsonNode comp : components) {
                    JsonNode types = comp.path("types");
                    String longName = comp.path("long_name").asText(null);
                    String shortName = comp.path("short_name").asText(null);

                    if (hasType(types, "postal_code")) {
                        postalCode = longName != null ? longName : shortName;
                    } else if (hasType(types, "locality")) {
                        city = longName != null ? longName : shortName;
                    } else if (city == null && hasType(types, "administrative_area_level_2")) {
                        city = longName != null ? longName : shortName;
                    } else if (hasType(types, "administrative_area_level_1")) {
                        state = longName != null ? longName : shortName;
                    } else if (hasType(types, "country")) {
                        country = longName != null ? longName : shortName;
                        countryCode = shortName != null ? shortName : longName;
                    }
                }
            }

            List<String> placeTypes = new ArrayList<>();
            JsonNode typesNode = bestResult.path("types");
            if (typesNode.isArray()) {
                for (JsonNode t : typesNode) {
                    placeTypes.add(t.asText());
                }
            }

            return VerifiedAddressResponse.builder()
                    .verified(true)
                    .placeId(placeId)
                    .formattedAddress(formattedAddress)
                    .latitude(lat)
                    .longitude(lng)
                    .city(city)
                    .state(state)
                    .postalCode(postalCode)
                    .country(country)
                    .countryCode(countryCode)
                    .placeTypes(placeTypes)
                    .build();

        } catch (AddressVerificationException | GoogleApiQuotaExceededException | GoogleApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse Google Geocoding JSON response: {}", e.getMessage(), e);
            throw new AddressVerificationException("Unable to parse Google Geocoding response: " + e.getMessage(), e);
        }
    }

    private void handleGeocodingStatus(String status) {
        switch (status) {
            case "OK":
                return;
            case "ZERO_RESULTS":
                throw new AddressVerificationException("Address or location could not be found");
            case "OVER_QUERY_LIMIT":
                throw new GoogleApiQuotaExceededException("Google Geocoding API quota exceeded");
            case "REQUEST_DENIED":
                throw new GoogleApiException("Google Geocoding request denied: Check server API key permissions");
            case "INVALID_REQUEST":
                throw new InvalidCoordinatesException("Invalid parameters supplied to Google Geocoding API");
            default:
                throw new GoogleApiException("Google Geocoding API returned unexpected status: " + status);
        }
    }

    private boolean hasType(JsonNode types, String expectedType) {
        if (types == null || !types.isArray()) {
            return false;
        }
        for (JsonNode type : types) {
            if (expectedType.equalsIgnoreCase(type.asText())) {
                return true;
            }
        }
        return false;
    }

    private void validateCoordinates(Double latitude, Double longitude) {
        if (latitude == null || latitude.isNaN() || latitude.isInfinite() || latitude < -90.0 || latitude > 90.0) {
            throw new InvalidCoordinatesException("Latitude must be between -90.0 and 90.0. Provided: " + latitude);
        }
        if (longitude == null || longitude.isNaN() || longitude.isInfinite() || longitude < -180.0 || longitude > 180.0) {
            throw new InvalidCoordinatesException("Longitude must be between -180.0 and 180.0. Provided: " + longitude);
        }
    }
}
