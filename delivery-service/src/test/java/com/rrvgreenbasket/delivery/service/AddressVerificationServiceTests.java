package com.rrvgreenbasket.delivery.service;

import com.rrvgreenbasket.delivery.config.DeliveryProperties;
import com.rrvgreenbasket.delivery.dto.response.ResolvedLocationResponse;
import com.rrvgreenbasket.delivery.dto.response.VerifiedAddressResponse;
import com.rrvgreenbasket.delivery.exception.AddressVerificationException;
import com.rrvgreenbasket.delivery.exception.CountryNotSupportedException;
import com.rrvgreenbasket.delivery.exception.InvalidCoordinatesException;
import com.rrvgreenbasket.delivery.exception.InvalidPlaceIdException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AddressVerificationService Unit Tests")
class AddressVerificationServiceTests {

    @Mock
    private GooglePlacesService googlePlacesService;

    @Mock
    private GoogleGeocodingService googleGeocodingService;

    @Mock
    private DeliveryProperties deliveryProperties;

    @InjectMocks
    private AddressVerificationService verificationService;

    @BeforeEach
    void setUp() {
        lenient().when(deliveryProperties.getDefaultCountry()).thenReturn("India");
        lenient().when(deliveryProperties.getDefaultCountryCode()).thenReturn("IN");
    }

    @Test
    @DisplayName("Verify Place ID - Valid Indian address succeeds")
    void testValidPlaceId() {
        VerifiedAddressResponse mockDetails = VerifiedAddressResponse.builder()
                .verified(true)
                .placeId("ChIJ7eXy0rP_yDsRzZqj9Y1Lw6s")
                .formattedAddress("Collector Office Road, Kurnool, Andhra Pradesh 518001, India")
                .latitude(15.8281)
                .longitude(78.0373)
                .city("Kurnool")
                .state("Andhra Pradesh")
                .postalCode("518001")
                .country("India")
                .countryCode("IN")
                .placeTypes(List.of("street_address"))
                .build();

        when(googlePlacesService.getPlaceDetails("ChIJ7eXy0rP_yDsRzZqj9Y1Lw6s")).thenReturn(mockDetails);

        VerifiedAddressResponse response = verificationService.verifyPlaceId("ChIJ7eXy0rP_yDsRzZqj9Y1Lw6s");

        assertThat(response).isNotNull();
        assertThat(response.isVerified()).isTrue();
        assertThat(response.getPlaceId()).isEqualTo("ChIJ7eXy0rP_yDsRzZqj9Y1Lw6s");
        assertThat(response.getLatitude()).isEqualTo(15.8281);
        assertThat(response.getLongitude()).isEqualTo(78.0373);
        assertThat(response.getCity()).isEqualTo("Kurnool");
        assertThat(response.getCountry()).isEqualTo("India");
    }

    @Test
    @DisplayName("Verify Place ID - Missing or blank Place ID throws InvalidPlaceIdException")
    void testBlankPlaceIdThrows() {
        assertThatThrownBy(() -> verificationService.verifyPlaceId(""))
                .isInstanceOf(InvalidPlaceIdException.class)
                .hasMessageContaining("mandatory");

        assertThatThrownBy(() -> verificationService.verifyPlaceId(null))
                .isInstanceOf(InvalidPlaceIdException.class);
    }

    @Test
    @DisplayName("Verify Place ID - Address in unsupported country throws CountryNotSupportedException")
    void testUnsupportedCountryThrows() {
        VerifiedAddressResponse usaAddress = VerifiedAddressResponse.builder()
                .verified(true)
                .placeId("ChIJd8BlQ2BZwokRAFUEcm_qrcA")
                .formattedAddress("1600 Amphitheatre Pkwy, Mountain View, CA 94043, USA")
                .latitude(37.4220)
                .longitude(-122.0841)
                .country("United States")
                .countryCode("US")
                .build();

        when(googlePlacesService.getPlaceDetails("ChIJd8BlQ2BZwokRAFUEcm_qrcA")).thenReturn(usaAddress);

        assertThatThrownBy(() -> verificationService.verifyPlaceId("ChIJd8BlQ2BZwokRAFUEcm_qrcA"))
                .isInstanceOf(CountryNotSupportedException.class)
                .hasMessageContaining("currently supported only in India");
    }

    @Test
    @DisplayName("Verify Place ID - Out-of-bounds coordinates throw InvalidCoordinatesException")
    void testOutOfBoundsCoordinatesThrows() {
        VerifiedAddressResponse badCoords = VerifiedAddressResponse.builder()
                .verified(true)
                .placeId("place-bad-coords")
                .formattedAddress("Somewhere, India")
                .latitude(95.0) // Invalid: > 90
                .longitude(78.0)
                .country("India")
                .countryCode("IN")
                .build();

        when(googlePlacesService.getPlaceDetails("place-bad-coords")).thenReturn(badCoords);

        assertThatThrownBy(() -> verificationService.verifyPlaceId("place-bad-coords"))
                .isInstanceOf(InvalidCoordinatesException.class)
                .hasMessageContaining("Invalid latitude");
    }

    @Test
    @DisplayName("Verify Place ID - Address without postal PIN code succeeds with null postal code")
    void testAddressWithoutPostalCodeSucceeds() {
        VerifiedAddressResponse noPinAddress = VerifiedAddressResponse.builder()
                .verified(true)
                .placeId("place-no-pin")
                .formattedAddress("Rural Kurnool Road, Andhra Pradesh, India")
                .latitude(15.8281)
                .longitude(78.0373)
                .city("Kurnool")
                .state("Andhra Pradesh")
                .postalCode(null)
                .country("India")
                .countryCode("IN")
                .build();

        when(googlePlacesService.getPlaceDetails("place-no-pin")).thenReturn(noPinAddress);

        VerifiedAddressResponse response = verificationService.verifyPlaceId("place-no-pin");
        assertThat(response.isVerified()).isTrue();
        assertThat(response.getPostalCode()).isNull();
        assertThat(response.getCity()).isEqualTo("Kurnool");
    }

    @Test
    @DisplayName("Reverse Geocoding - Valid coordinates resolve to address")
    void testReverseGeocodingSuccess() {
        ResolvedLocationResponse mockResolved = ResolvedLocationResponse.builder()
                .verified(true)
                .placeId("ChIJ7eXy0rP_yDsRzZqj9Y1Lw6s")
                .formattedAddress("Near Collector Office, Kurnool, Andhra Pradesh 518001, India")
                .latitude(15.8281)
                .longitude(78.0373)
                .city("Kurnool")
                .state("Andhra Pradesh")
                .postalCode("518001")
                .country("India")
                .build();

        when(googleGeocodingService.reverseGeocode(15.8281, 78.0373)).thenReturn(mockResolved);

        ResolvedLocationResponse response = verificationService.resolveCurrentLocation(15.8281, 78.0373);

        assertThat(response).isNotNull();
        assertThat(response.isVerified()).isTrue();
        assertThat(response.getPlaceId()).isEqualTo("ChIJ7eXy0rP_yDsRzZqj9Y1Lw6s");
        assertThat(response.getCity()).isEqualTo("Kurnool");
        assertThat(response.getCountry()).isEqualTo("India");
    }

    @Test
    @DisplayName("Reverse Geocoding - Invalid latitude/longitude throws InvalidCoordinatesException")
    void testReverseGeocodingInvalidCoordinatesThrows() {
        assertThatThrownBy(() -> verificationService.resolveCurrentLocation(91.0, 78.0))
                .isInstanceOf(InvalidCoordinatesException.class);

        assertThatThrownBy(() -> verificationService.resolveCurrentLocation(15.0, 185.0))
                .isInstanceOf(InvalidCoordinatesException.class);

        assertThatThrownBy(() -> verificationService.resolveCurrentLocation(null, 78.0))
                .isInstanceOf(InvalidCoordinatesException.class);
    }

    @Test
    @DisplayName("Reverse Geocoding - Foreign coordinate location throws CountryNotSupportedException")
    void testReverseGeocodingForeignLocationThrows() {
        ResolvedLocationResponse foreignLocation = ResolvedLocationResponse.builder()
                .verified(true)
                .formattedAddress("Dubai, United Arab Emirates")
                .latitude(25.2048)
                .longitude(55.2708)
                .country("United Arab Emirates")
                .build();

        when(googleGeocodingService.reverseGeocode(25.2048, 55.2708)).thenReturn(foreignLocation);

        assertThatThrownBy(() -> verificationService.resolveCurrentLocation(25.2048, 55.2708))
                .isInstanceOf(CountryNotSupportedException.class);
    }
}
