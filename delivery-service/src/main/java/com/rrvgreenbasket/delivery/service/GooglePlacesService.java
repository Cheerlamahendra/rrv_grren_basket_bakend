package com.rrvgreenbasket.delivery.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrvgreenbasket.delivery.config.GoogleMapsProperties;
import com.rrvgreenbasket.delivery.dto.response.VerifiedAddressResponse;
import com.rrvgreenbasket.delivery.exception.AddressVerificationException;
import com.rrvgreenbasket.delivery.exception.GoogleApiException;
import com.rrvgreenbasket.delivery.exception.GoogleApiQuotaExceededException;
import com.rrvgreenbasket.delivery.exception.InvalidPlaceIdException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Service integrating with Google Places API (New) for address verification and place detail retrieval.
 */
@Slf4j
@Service
public class GooglePlacesService {

    private static final String FIELD_MASK = "id,displayName,formattedAddress,location,addressComponents,types";

    private final RestClient restClient;
    private final GoogleMapsProperties properties;
    private final ObjectMapper objectMapper;

    public GooglePlacesService(
            @Qualifier("googlePlacesRestClient") RestClient restClient,
            GoogleMapsProperties properties,
            ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * Retrieves place details from Google Places API (New) using the Place ID.
     *
     * @param placeId Google Place ID
     * @return VerifiedAddressResponse containing verified coordinates, formatted address, and address components
     */
    public VerifiedAddressResponse getPlaceDetails(String placeId) {
        if (!StringUtils.hasText(placeId)) {
            throw new InvalidPlaceIdException("Google Place ID cannot be null or empty");
        }

        String apiKey = properties.getServerApiKey();
        if (!StringUtils.hasText(apiKey)) {
            log.error("Google Maps Server API key is not configured");
            throw new GoogleApiException("Google Maps Server API key is not configured");
        }

        log.debug("Calling Google Places API (New) for placeId: {}", placeId);

        try {
            String responseBody = restClient.get()
                    .uri("/v1/places/{placeId}", placeId.trim())
                    .header("X-Goog-Api-Key", apiKey.trim())
                    .header("X-Goog-FieldMask", FIELD_MASK)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                        int code = response.getStatusCode().value();
                        if (code == 404 || code == 400) {
                            throw new InvalidPlaceIdException("Invalid or non-existent Google Place ID: " + placeId);
                        } else if (code == 429) {
                            throw new GoogleApiQuotaExceededException("Google Places API quota exceeded or rate limited");
                        }
                        throw new AddressVerificationException("Google Places client error: HTTP " + code);
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (request, response) -> {
                        throw new GoogleApiException("Google Places API server error: HTTP " + response.getStatusCode().value());
                    })
                    .body(String.class);

            if (!StringUtils.hasText(responseBody)) {
                throw new AddressVerificationException("Empty response received from Google Places API");
            }

            return parsePlacesResponse(responseBody, placeId);

        } catch (InvalidPlaceIdException | GoogleApiQuotaExceededException | AddressVerificationException | GoogleApiException e) {
            throw e;
        } catch (HttpClientErrorException.NotFound | HttpClientErrorException.BadRequest e) {
            log.warn("Google Places rejected placeId '{}': {}", placeId, e.getMessage());
            throw new InvalidPlaceIdException("Invalid Google Place ID: " + placeId);
        } catch (HttpClientErrorException.TooManyRequests e) {
            log.error("Google Places quota exceeded: {}", e.getMessage());
            throw new GoogleApiQuotaExceededException("Google Places API rate limit exceeded");
        } catch (Exception e) {
            log.error("Unexpected failure calling Google Places API for placeId '{}': {}", placeId, e.getMessage());
            throw new GoogleApiException("Failed to communicate with Google Places API: " + e.getMessage(), e);
        }
    }

    private VerifiedAddressResponse parsePlacesResponse(String json, String requestedPlaceId) {
        try {
            JsonNode root = objectMapper.readTree(json);

            String id = root.path("id").asText(requestedPlaceId);
            String formattedAddress = root.path("formattedAddress").asText(null);

            JsonNode locationNode = root.path("location");
            if (locationNode.isMissingNode() || !locationNode.has("latitude") || !locationNode.has("longitude")) {
                throw new AddressVerificationException("Google Places response did not contain geographical coordinates");
            }

            double latitude = locationNode.path("latitude").asDouble();
            double longitude = locationNode.path("longitude").asDouble();

            validateCoordinates(latitude, longitude);

            // Extract address components
            String city = null;
            String state = null;
            String postalCode = null;
            String country = null;
            String countryCode = null;

            JsonNode components = root.path("addressComponents");
            if (components.isArray()) {
                for (JsonNode comp : components) {
                    JsonNode types = comp.path("types");
                    String longText = comp.path("longText").asText(null);
                    String shortText = comp.path("shortText").asText(null);

                    if (hasType(types, "postal_code")) {
                        postalCode = longText != null ? longText : shortText;
                    } else if (hasType(types, "locality")) {
                        city = longText != null ? longText : shortText;
                    } else if (city == null && hasType(types, "administrative_area_level_2")) {
                        city = longText != null ? longText : shortText;
                    } else if (hasType(types, "administrative_area_level_1")) {
                        state = longText != null ? longText : shortText;
                    } else if (hasType(types, "country")) {
                        country = longText != null ? longText : shortText;
                        countryCode = shortText != null ? shortText : longText;
                    }
                }
            }

            // Extract place types
            List<String> placeTypes = new ArrayList<>();
            JsonNode typesNode = root.path("types");
            if (typesNode.isArray()) {
                for (JsonNode t : typesNode) {
                    placeTypes.add(t.asText());
                }
            }

            return VerifiedAddressResponse.builder()
                    .verified(true)
                    .placeId(id)
                    .formattedAddress(formattedAddress)
                    .latitude(latitude)
                    .longitude(longitude)
                    .city(city)
                    .state(state)
                    .postalCode(postalCode)
                    .country(country)
                    .countryCode(countryCode)
                    .placeTypes(placeTypes)
                    .build();

        } catch (AddressVerificationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse Google Places JSON response: {}", e.getMessage(), e);
            throw new AddressVerificationException("Unable to parse Google Places response: " + e.getMessage(), e);
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

    private void validateCoordinates(double latitude, double longitude) {
        if (Double.isNaN(latitude) || Double.isInfinite(latitude) || latitude < -90.0 || latitude > 90.0) {
            throw new AddressVerificationException("Verified address latitude out of valid range [-90, 90]: " + latitude);
        }
        if (Double.isNaN(longitude) || Double.isInfinite(longitude) || longitude < -180.0 || longitude > 180.0) {
            throw new AddressVerificationException("Verified address longitude out of valid range [-180, 180]: " + longitude);
        }
    }
}
