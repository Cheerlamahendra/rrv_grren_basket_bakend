package com.rrvgreenbasket.delivery.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrvgreenbasket.delivery.config.GoogleMapsProperties;
import com.rrvgreenbasket.delivery.exception.GoogleApiException;
import com.rrvgreenbasket.delivery.exception.GoogleApiQuotaExceededException;
import com.rrvgreenbasket.delivery.exception.InvalidCoordinatesException;
import com.rrvgreenbasket.delivery.exception.RouteNotFoundException;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Service integrating with Google Routes API for actual road-distance and driving duration calculations.
 */
@Slf4j
@Service
public class GoogleRoutesService {

    private static final String FIELD_MASK = "routes.distanceMeters,routes.duration";

    private final RestClient restClient;
    private final GoogleMapsProperties properties;
    private final ObjectMapper objectMapper;

    public GoogleRoutesService(
            @Qualifier("googleRoutesRestClient") RestClient restClient,
            GoogleMapsProperties properties,
            ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Builder
    public record RouteResult(
            int distanceMeters,
            double distanceKm,
            String duration
    ) {}

    /**
     * Computes actual driving road distance and travel duration between store and customer coordinates.
     *
     * @param originLat Store latitude
     * @param originLng Store longitude
     * @param destLat   Customer latitude
     * @param destLng   Customer longitude
     * @return RouteResult containing road distance in meters/km and duration
     */
    public RouteResult computeRoute(double originLat, double originLng, double destLat, double destLng) {
        validateCoordinates(originLat, originLng, "Store origin");
        validateCoordinates(destLat, destLng, "Customer destination");

        String apiKey = properties.getServerApiKey();
        if (!StringUtils.hasText(apiKey)) {
            log.error("Google Maps Server API key is not configured");
            throw new GoogleApiException("Google Maps Server API key is not configured");
        }

        log.debug("Calling Google Routes API: origin=[{}, {}], dest=[{}, {}]", originLat, originLng, destLat, destLng);

        Map<String, Object> requestBody = Map.of(
                "origin", Map.of(
                        "location", Map.of(
                                "latLng", Map.of(
                                        "latitude", originLat,
                                        "longitude", originLng
                                )
                        )
                ),
                "destination", Map.of(
                        "location", Map.of(
                                "latLng", Map.of(
                                        "latitude", destLat,
                                        "longitude", destLng
                                )
                        )
                ),
                "travelMode", "DRIVE",
                "routingPreference", "TRAFFIC_UNAWARE"
        );

        try {
            String responseBody = restClient.post()
                    .uri("/directions/v2:computeRoutes")
                    .header("X-Goog-Api-Key", apiKey.trim())
                    .header("X-Goog-FieldMask", FIELD_MASK)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                        int code = response.getStatusCode().value();
                        if (code == 429) {
                            throw new GoogleApiQuotaExceededException("Google Routes API rate limit exceeded");
                        }
                        throw new GoogleApiException("Google Routes API client error: HTTP " + code);
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (request, response) -> {
                        throw new GoogleApiException("Google Routes API server error: HTTP " + response.getStatusCode().value());
                    })
                    .body(String.class);

            if (!StringUtils.hasText(responseBody)) {
                throw new RouteNotFoundException("Empty response received from Google Routes API");
            }

            return parseRoutesResponse(responseBody, originLat, originLng, destLat, destLng);

        } catch (RouteNotFoundException | GoogleApiQuotaExceededException | GoogleApiException e) {
            throw e;
        } catch (HttpClientErrorException.TooManyRequests e) {
            log.error("Google Routes rate limit exceeded: {}", e.getMessage());
            throw new GoogleApiQuotaExceededException("Google Routes API rate limit exceeded");
        } catch (Exception e) {
            log.error("Google Routes API call failed: {}", e.getMessage());
            throw new GoogleApiException("Failed to communicate with Google Routes API: " + e.getMessage(), e);
        }
    }

    private RouteResult parseRoutesResponse(String json, double originLat, double originLng, double destLat, double destLng) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode routes = root.path("routes");

            if (!routes.isArray() || routes.isEmpty()) {
                log.warn("No routes found between origin=[{}, {}] and dest=[{}, {}]", originLat, originLng, destLat, destLng);
                throw new RouteNotFoundException("No driving route found between store and customer location");
            }

            JsonNode firstRoute = routes.get(0);
            if (!firstRoute.has("distanceMeters")) {
                throw new RouteNotFoundException("Route distance data not available in Google Routes response");
            }

            int distanceMeters = firstRoute.path("distanceMeters").asInt();
            String duration = firstRoute.path("duration").asText("N/A");

            // Convert distanceMeters to distanceKm rounded to 2 decimal places for display
            double distanceKm = BigDecimal.valueOf(distanceMeters)
                    .divide(BigDecimal.valueOf(1000), 2, RoundingMode.HALF_UP)
                    .doubleValue();

            log.debug("Computed route: distanceMeters={}, distanceKm={}, duration={}", distanceMeters, distanceKm, duration);

            return RouteResult.builder()
                    .distanceMeters(distanceMeters)
                    .distanceKm(distanceKm)
                    .duration(duration)
                    .build();

        } catch (RouteNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse Google Routes response: {}", e.getMessage(), e);
            throw new GoogleApiException("Unable to parse Google Routes response: " + e.getMessage(), e);
        }
    }

    private void validateCoordinates(double latitude, double longitude, String label) {
        if (Double.isNaN(latitude) || Double.isInfinite(latitude) || latitude < -90.0 || latitude > 90.0) {
            throw new InvalidCoordinatesException(label + " latitude must be between -90.0 and 90.0. Provided: " + latitude);
        }
        if (Double.isNaN(longitude) || Double.isInfinite(longitude) || longitude < -180.0 || longitude > 180.0) {
            throw new InvalidCoordinatesException(label + " longitude must be between -180.0 and 180.0. Provided: " + longitude);
        }
    }
}
