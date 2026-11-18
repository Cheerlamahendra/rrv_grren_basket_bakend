package com.rrvgreenbasket.delivery.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrvgreenbasket.delivery.config.GoogleMapsProperties;
import com.rrvgreenbasket.delivery.dto.response.ResolvedLocationResponse;
import com.rrvgreenbasket.delivery.dto.response.VerifiedAddressResponse;
import com.rrvgreenbasket.delivery.exception.AddressVerificationException;
import com.rrvgreenbasket.delivery.exception.GoogleApiQuotaExceededException;
import com.rrvgreenbasket.delivery.exception.InvalidPlaceIdException;
import com.rrvgreenbasket.delivery.exception.RouteNotFoundException;
import com.rrvgreenbasket.delivery.service.GoogleRoutesService.RouteResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@DisplayName("Google Maps Services Direct Integration Tests")
class GoogleMapsServiceTests {

    private ObjectMapper objectMapper;
    private GoogleMapsProperties properties;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        properties = new GoogleMapsProperties();
        properties.setServerApiKey("test-server-api-key");
    }

    @Test
    @DisplayName("Google Places - Successful Place Details parsing")
    void testPlacesSuccess() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://places.googleapis.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        GooglePlacesService placesService = new GooglePlacesService(restClient, properties, objectMapper);

        String json = """
                {
                  "id": "ChIJ7eXy0rP_yDsRzZqj9Y1Lw6s",
                  "types": ["street_address", "geocode"],
                  "formattedAddress": "Collector Office Road, Kurnool, Andhra Pradesh 518001, India",
                  "location": {
                    "latitude": 15.8281,
                    "longitude": 78.0373
                  },
                  "addressComponents": [
                    {"longText": "518001", "shortText": "518001", "types": ["postal_code"]},
                    {"longText": "Kurnool", "shortText": "Kurnool", "types": ["locality"]},
                    {"longText": "Andhra Pradesh", "shortText": "AP", "types": ["administrative_area_level_1"]},
                    {"longText": "India", "shortText": "IN", "types": ["country"]}
                  ]
                }
                """;

        server.expect(requestTo("https://places.googleapis.com/v1/places/ChIJ7eXy0rP_yDsRzZqj9Y1Lw6s"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Goog-Api-Key", "test-server-api-key"))
                .andExpect(header("X-Goog-FieldMask", "id,displayName,formattedAddress,location,addressComponents,types"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        VerifiedAddressResponse response = placesService.getPlaceDetails("ChIJ7eXy0rP_yDsRzZqj9Y1Lw6s");

        assertThat(response.isVerified()).isTrue();
        assertThat(response.getPlaceId()).isEqualTo("ChIJ7eXy0rP_yDsRzZqj9Y1Lw6s");
        assertThat(response.getLatitude()).isEqualTo(15.8281);
        assertThat(response.getLongitude()).isEqualTo(78.0373);
        assertThat(response.getCity()).isEqualTo("Kurnool");
        assertThat(response.getState()).isEqualTo("Andhra Pradesh");
        assertThat(response.getPostalCode()).isEqualTo("518001");
        assertThat(response.getCountry()).isEqualTo("India");
        assertThat(response.getCountryCode()).isEqualTo("IN");
        server.verify();
    }

    @Test
    @DisplayName("Google Places - Place ID not found returns 404 throwing InvalidPlaceIdException")
    void testPlacesNotFound() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://places.googleapis.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        GooglePlacesService placesService = new GooglePlacesService(restClient, properties, objectMapper);

        server.expect(requestTo("https://places.googleapis.com/v1/places/invalid-place-id"))
                .andRespond(withResourceNotFound());

        assertThatThrownBy(() -> placesService.getPlaceDetails("invalid-place-id"))
                .isInstanceOf(InvalidPlaceIdException.class)
                .hasMessageContaining("Invalid");
        server.verify();
    }

    @Test
    @DisplayName("Google Places - Quota exceeded (429) throws GoogleApiQuotaExceededException")
    void testPlacesQuotaExceeded() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://places.googleapis.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        GooglePlacesService placesService = new GooglePlacesService(restClient, properties, objectMapper);

        server.expect(requestTo("https://places.googleapis.com/v1/places/place-quota"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> placesService.getPlaceDetails("place-quota"))
                .isInstanceOf(GoogleApiQuotaExceededException.class);
        server.verify();
    }

    @Test
    @DisplayName("Google Geocoding - Successful Reverse Geocoding")
    void testGeocodingReverseSuccess() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://maps.googleapis.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        GoogleGeocodingService geocodingService = new GoogleGeocodingService(restClient, properties, objectMapper);

        String json = """
                {
                  "status": "OK",
                  "results": [
                    {
                      "place_id": "ChIJ7eXy0rP_yDsRzZqj9Y1Lw6s",
                      "formatted_address": "Collector Office Road, Kurnool, Andhra Pradesh 518001, India",
                      "geometry": {
                        "location": {"lat": 15.8281, "lng": 78.0373}
                      },
                      "address_components": [
                        {"long_name": "518001", "short_name": "518001", "types": ["postal_code"]},
                        {"long_name": "Kurnool", "short_name": "Kurnool", "types": ["locality"]},
                        {"long_name": "Andhra Pradesh", "short_name": "AP", "types": ["administrative_area_level_1"]},
                        {"long_name": "India", "short_name": "IN", "types": ["country"]}
                      ]
                    }
                  ]
                }
                """;

        server.expect(requestTo("https://maps.googleapis.com/maps/api/geocode/json?latlng=15.828100,78.037300&key=test-server-api-key"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        ResolvedLocationResponse response = geocodingService.reverseGeocode(15.8281, 78.0373);

        assertThat(response.isVerified()).isTrue();
        assertThat(response.getPlaceId()).isEqualTo("ChIJ7eXy0rP_yDsRzZqj9Y1Lw6s");
        assertThat(response.getCity()).isEqualTo("Kurnool");
        assertThat(response.getCountry()).isEqualTo("India");
        server.verify();
    }

    @Test
    @DisplayName("Google Routes - Successful Route computation")
    void testRoutesSuccess() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://routes.googleapis.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        GoogleRoutesService routesService = new GoogleRoutesService(restClient, properties, objectMapper);

        String json = """
                {
                  "routes": [
                    {
                      "distanceMeters": 2350,
                      "duration": "600s"
                    }
                  ]
                }
                """;

        server.expect(requestTo("https://routes.googleapis.com/directions/v2:computeRoutes"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Goog-Api-Key", "test-server-api-key"))
                .andExpect(header("X-Goog-FieldMask", "routes.distanceMeters,routes.duration"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        RouteResult result = routesService.computeRoute(15.8281, 78.0373, 15.8300, 78.0400);

        assertThat(result.distanceMeters()).isEqualTo(2350);
        assertThat(result.distanceKm()).isEqualTo(2.35);
        assertThat(result.duration()).isEqualTo("600s");
        server.verify();
    }

    @Test
    @DisplayName("Google Routes - Empty routes array throws RouteNotFoundException")
    void testRoutesNotFound() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://routes.googleapis.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        GoogleRoutesService routesService = new GoogleRoutesService(restClient, properties, objectMapper);

        String json = """
                {
                  "routes": []
                }
                """;

        server.expect(requestTo("https://routes.googleapis.com/directions/v2:computeRoutes"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> routesService.computeRoute(15.8281, 78.0373, 15.8300, 78.0400))
                .isInstanceOf(RouteNotFoundException.class)
                .hasMessageContaining("No driving route found");
        server.verify();
    }
}
