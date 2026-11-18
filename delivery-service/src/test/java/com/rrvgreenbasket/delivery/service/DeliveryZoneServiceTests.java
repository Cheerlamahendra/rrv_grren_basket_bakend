package com.rrvgreenbasket.delivery.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrvgreenbasket.delivery.dto.request.CheckDeliveryAvailabilityRequest;
import com.rrvgreenbasket.delivery.dto.request.CreateDeliveryZoneRequest;
import com.rrvgreenbasket.delivery.dto.request.UpdateDeliveryZoneRequest;
import com.rrvgreenbasket.delivery.dto.response.DeliveryAvailabilityResponse;
import com.rrvgreenbasket.delivery.dto.response.DeliveryZoneResponse;
import com.rrvgreenbasket.delivery.entity.DeliveryZone;
import com.rrvgreenbasket.delivery.exception.DuplicateResourceException;
import com.rrvgreenbasket.delivery.exception.InvalidRequestException;
import com.rrvgreenbasket.delivery.exception.ZoneNotFoundException;
import com.rrvgreenbasket.delivery.exception.ZoneOverlapException;
import com.rrvgreenbasket.delivery.geocoding.ReverseGeocodingService;
import com.rrvgreenbasket.delivery.geometry.GeometryService;
import com.rrvgreenbasket.delivery.repository.DeliveryZoneRepository;
import com.rrvgreenbasket.delivery.service.impl.DeliveryZoneServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DeliveryZoneService Business Logic Tests")
class DeliveryZoneServiceTests {

    @Mock
    private DeliveryZoneRepository deliveryZoneRepository;

    @Mock
    private ReverseGeocodingService reverseGeocodingService;

    @Spy
    private GeometryService geometryService = new GeometryService(new ObjectMapper());

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private DeliveryZoneServiceImpl deliveryZoneService;

    private static final String ZONE_1_GEOJSON = """
            {
              "type": "Polygon",
              "coordinates": [
                [
                  [78.0300, 15.8200],
                  [78.0500, 15.8200],
                  [78.0500, 15.8400],
                  [78.0300, 15.8400],
                  [78.0300, 15.8200]
                ]
              ]
            }
            """;

    private JsonNode boundaryNode;

    @BeforeEach
    void setUp() throws Exception {
        boundaryNode = objectMapper.readTree(ZONE_1_GEOJSON);
    }

    @Test
    @DisplayName("1. Successfully creates new delivery zone")
    void testCreateZone_Success() {
        CreateDeliveryZoneRequest request = CreateDeliveryZoneRequest.builder()
                .zoneName("Kurnool Zone 1")
                .zoneCode("KUR-ZONE-01")
                .description("Delivery coverage for Kurnool")
                .city("Kurnool")
                .state("Andhra Pradesh")
                .pincode("518001")
                .pincodes(Set.of("518001", "518002"))
                .deliveryCharge(new BigDecimal("30.00"))
                .minimumOrderAmount(new BigDecimal("199.00"))
                .freeDeliveryThreshold(new BigDecimal("499.00"))
                .estimatedDeliveryTime("NEXT_DAY")
                .active(true)
                .boundary(boundaryNode)
                .build();

        when(deliveryZoneRepository.existsByZoneCodeAndDeletedFalse("KUR-ZONE-01")).thenReturn(false);
        when(deliveryZoneRepository.existsByZoneCode("KUR-ZONE-01")).thenReturn(false);
        when(deliveryZoneRepository.findByActiveTrueAndDeletedFalse()).thenReturn(Collections.emptyList());
        when(deliveryZoneRepository.save(any(DeliveryZone.class))).thenAnswer(invocation -> {
            DeliveryZone z = invocation.getArgument(0);
            z.setId(1L);
            return z;
        });

        DeliveryZoneResponse response = deliveryZoneService.createZone(request, "admin-1");

        assertNotNull(response);
        assertEquals("Kurnool Zone 1", response.getZoneName());
        assertEquals("KUR-ZONE-01", response.getZoneCode());
        assertEquals("Kurnool", response.getCity());
        assertEquals("Andhra Pradesh", response.getState());
        assertEquals(new BigDecimal("30.00"), response.getDeliveryCharge());
        assertEquals(new BigDecimal("199.00"), response.getMinimumOrderAmount());
        assertEquals(new BigDecimal("499.00"), response.getFreeDeliveryThreshold());
        assertTrue(response.isActive());
        verify(deliveryZoneRepository).save(any(DeliveryZone.class));
    }

    @Test
    @DisplayName("2. Rejects duplicate zone code")
    void testCreateZone_DuplicateCode() {
        CreateDeliveryZoneRequest request = CreateDeliveryZoneRequest.builder()
                .zoneName("Duplicate Zone")
                .zoneCode("KUR-ZONE-01")
                .city("Kurnool")
                .state("Andhra Pradesh")
                .deliveryCharge(new BigDecimal("30.00"))
                .minimumOrderAmount(new BigDecimal("199.00"))
                .boundary(boundaryNode)
                .build();

        when(deliveryZoneRepository.existsByZoneCodeAndDeletedFalse("KUR-ZONE-01")).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> deliveryZoneService.createZone(request, "admin-1"));
        verify(deliveryZoneRepository, never()).save(any());
    }

    @Test
    @DisplayName("3. Rejects overlapping active delivery zone in same city")
    void testCreateZone_OverlappingRejected() throws Exception {
        String overlappingJson = """
                {
                  "type": "Polygon",
                  "coordinates": [
                    [
                      [78.0400, 15.8300],
                      [78.0600, 15.8300],
                      [78.0600, 15.8500],
                      [78.0400, 15.8500],
                      [78.0400, 15.8300]
                    ]
                  ]
                }
                """;

        DeliveryZone existingZone = DeliveryZone.builder()
                .id(1L)
                .publicId(UUID.randomUUID().toString())
                .zoneName("Existing Zone")
                .zoneCode("KUR-EXISTING")
                .city("Kurnool")
                .state("Andhra Pradesh")
                .boundaryGeojson(ZONE_1_GEOJSON)
                .active(true)
                .deleted(false)
                .build();

        when(deliveryZoneRepository.existsByZoneCodeAndDeletedFalse("KUR-OVERLAP")).thenReturn(false);
        when(deliveryZoneRepository.existsByZoneCode("KUR-OVERLAP")).thenReturn(false);
        when(deliveryZoneRepository.findByActiveTrueAndDeletedFalse()).thenReturn(List.of(existingZone));

        CreateDeliveryZoneRequest request = CreateDeliveryZoneRequest.builder()
                .zoneName("Overlapping Zone")
                .zoneCode("KUR-OVERLAP")
                .city("Kurnool")
                .state("Andhra Pradesh")
                .deliveryCharge(new BigDecimal("30.00"))
                .minimumOrderAmount(new BigDecimal("199.00"))
                .active(true)
                .boundary(objectMapper.readTree(overlappingJson))
                .build();

        assertThrows(ZoneOverlapException.class, () -> deliveryZoneService.createZone(request, "admin-1"));
        verify(deliveryZoneRepository, never()).save(any());
    }

    @Test
    @DisplayName("4. Hybrid validation succeeds with correct city, state, PIN, and coordinates inside polygon")
    void testHybridAvailability_Success() {
        DeliveryZone activeZone = DeliveryZone.builder()
                .id(1L)
                .publicId("zone-uuid-1")
                .zoneName("Kurnool Zone 1")
                .city("Kurnool")
                .state("Andhra Pradesh")
                .pincode("518001")
                .pincodes(new HashSet<>(Set.of("518001", "518002")))
                .deliveryCharge(new BigDecimal("30.00"))
                .minimumOrderAmount(new BigDecimal("199.00"))
                .freeDeliveryThreshold(new BigDecimal("499.00"))
                .estimatedDeliveryTime("NEXT_DAY")
                .boundaryGeojson(ZONE_1_GEOJSON)
                .active(true)
                .deleted(false)
                .build();

        when(deliveryZoneRepository.findByCityIgnoreCaseAndStateIgnoreCaseAndActiveTrueAndDeletedFalse("kurnool", "andhra pradesh"))
                .thenReturn(List.of(activeZone));

        CheckDeliveryAvailabilityRequest request = CheckDeliveryAvailabilityRequest.builder()
                .latitude(15.8300)
                .longitude(78.0400)
                .city("Kurnool")
                .state("Andhra Pradesh")
                .pincode("518001")
                .address("Near RTC Bus Stand")
                .build();

        DeliveryAvailabilityResponse result = deliveryZoneService.checkDeliveryAvailability(request);

        assertTrue(result.isAvailable());
        assertEquals("zone-uuid-1", result.getZonePublicId());
        assertEquals("Kurnool Zone 1", result.getZoneName());
        assertEquals(new BigDecimal("30.00"), result.getDeliveryCharge());
        assertEquals("Delivery is available in your selected area", result.getMessage());
    }

    @Test
    @DisplayName("5. Hybrid validation rejects wrong PIN code when zone enforces PIN matching")
    void testHybridAvailability_WrongPincode() {
        DeliveryZone activeZone = DeliveryZone.builder()
                .id(1L)
                .publicId("zone-uuid-1")
                .zoneName("Kurnool Zone 1")
                .city("Kurnool")
                .state("Andhra Pradesh")
                .pincode("518001")
                .pincodes(new HashSet<>(Set.of("518001")))
                .boundaryGeojson(ZONE_1_GEOJSON)
                .active(true)
                .deleted(false)
                .build();

        when(deliveryZoneRepository.findByCityIgnoreCaseAndStateIgnoreCaseAndActiveTrueAndDeletedFalse("kurnool", "andhra pradesh"))
                .thenReturn(List.of(activeZone));

        CheckDeliveryAvailabilityRequest request = CheckDeliveryAvailabilityRequest.builder()
                .latitude(15.8300)
                .longitude(78.0400)
                .city("Kurnool")
                .state("Andhra Pradesh")
                .pincode("518099") // Wrong PIN
                .build();

        DeliveryAvailabilityResponse result = deliveryZoneService.checkDeliveryAvailability(request);

        assertFalse(result.isAvailable());
        assertNull(result.getZonePublicId());
        assertTrue(result.getMessage().contains("PIN code 518099"));
    }

    @Test
    @DisplayName("6. Hybrid validation rejects when city has no active delivery zones")
    void testHybridAvailability_WrongCity() {
        when(deliveryZoneRepository.findByCityIgnoreCaseAndStateIgnoreCaseAndActiveTrueAndDeletedFalse("hyderabad", "telangana"))
                .thenReturn(Collections.emptyList());

        CheckDeliveryAvailabilityRequest request = CheckDeliveryAvailabilityRequest.builder()
                .latitude(17.3850)
                .longitude(78.4867)
                .city("Hyderabad")
                .state("Telangana")
                .pincode("500001")
                .build();

        DeliveryAvailabilityResponse result = deliveryZoneService.checkDeliveryAvailability(request);

        assertFalse(result.isAvailable());
        assertTrue(result.getMessage().contains("Hyderabad"));
    }

    @Test
    @DisplayName("7. Hybrid validation rejects when coordinates are outside configured polygon")
    void testHybridAvailability_CoordinatesOutsidePolygon() {
        DeliveryZone activeZone = DeliveryZone.builder()
                .id(1L)
                .publicId("zone-uuid-1")
                .zoneName("Kurnool Zone 1")
                .city("Kurnool")
                .state("Andhra Pradesh")
                .pincode("518001")
                .boundaryGeojson(ZONE_1_GEOJSON)
                .active(true)
                .deleted(false)
                .build();

        when(deliveryZoneRepository.findByCityIgnoreCaseAndStateIgnoreCaseAndActiveTrueAndDeletedFalse("kurnool", "andhra pradesh"))
                .thenReturn(List.of(activeZone));

        // Coordinate far away from polygon: lat=15.95, lng=78.20
        CheckDeliveryAvailabilityRequest request = CheckDeliveryAvailabilityRequest.builder()
                .latitude(15.9500)
                .longitude(78.2000)
                .city("Kurnool")
                .state("Andhra Pradesh")
                .pincode("518001")
                .build();

        DeliveryAvailabilityResponse result = deliveryZoneService.checkDeliveryAvailability(request);

        assertFalse(result.isAvailable());
    }

    @Test
    @DisplayName("8. Hybrid validation accepts coordinates on polygon perimeter boundary")
    void testHybridAvailability_CoordinatesOnBoundary() {
        DeliveryZone activeZone = DeliveryZone.builder()
                .id(1L)
                .publicId("zone-uuid-1")
                .zoneName("Kurnool Zone 1")
                .city("Kurnool")
                .state("Andhra Pradesh")
                .pincode("518001")
                .boundaryGeojson(ZONE_1_GEOJSON)
                .active(true)
                .deleted(false)
                .build();

        when(deliveryZoneRepository.findByCityIgnoreCaseAndStateIgnoreCaseAndActiveTrueAndDeletedFalse("kurnool", "andhra pradesh"))
                .thenReturn(List.of(activeZone));

        // Point exactly on boundary: lat=15.8200, lng=78.0400
        CheckDeliveryAvailabilityRequest request = CheckDeliveryAvailabilityRequest.builder()
                .latitude(15.8200)
                .longitude(78.0400)
                .city("Kurnool")
                .state("Andhra Pradesh")
                .pincode("518001")
                .build();

        DeliveryAvailabilityResponse result = deliveryZoneService.checkDeliveryAvailability(request);

        assertTrue(result.isAvailable());
        assertEquals("zone-uuid-1", result.getZonePublicId());
    }

    @Test
    @DisplayName("9. Zone without PIN restriction accepts any PIN code inside polygon")
    void testHybridAvailability_ZoneWithoutPincodeRestriction() {
        DeliveryZone activeZone = DeliveryZone.builder()
                .id(1L)
                .publicId("zone-uuid-no-pin")
                .zoneName("Open Kurnool Zone")
                .city("Kurnool")
                .state("Andhra Pradesh")
                .pincode(null) // No PIN restriction
                .pincodes(new HashSet<>())
                .boundaryGeojson(ZONE_1_GEOJSON)
                .active(true)
                .deleted(false)
                .build();

        when(deliveryZoneRepository.findByCityIgnoreCaseAndStateIgnoreCaseAndActiveTrueAndDeletedFalse("kurnool", "andhra pradesh"))
                .thenReturn(List.of(activeZone));

        CheckDeliveryAvailabilityRequest request = CheckDeliveryAvailabilityRequest.builder()
                .latitude(15.8300)
                .longitude(78.0400)
                .city("Kurnool")
                .state("Andhra Pradesh")
                .pincode("518005")
                .build();

        DeliveryAvailabilityResponse result = deliveryZoneService.checkDeliveryAvailability(request);

        assertTrue(result.isAvailable());
        assertEquals("zone-uuid-no-pin", result.getZonePublicId());
    }

    @Test
    @DisplayName("10. Throws InvalidRequestException when city is supplied without state")
    void testCityWithoutState_ThrowsException() {
        CheckDeliveryAvailabilityRequest request = CheckDeliveryAvailabilityRequest.builder()
                .latitude(15.8300)
                .longitude(78.0400)
                .city("Kurnool")
                .state(null) // Missing state
                .build();

        assertThrows(InvalidRequestException.class, () -> deliveryZoneService.checkDeliveryAvailability(request));
    }

    @Test
    @DisplayName("11. Throws InvalidRequestException when state is supplied without city")
    void testStateWithoutCity_ThrowsException() {
        CheckDeliveryAvailabilityRequest request = CheckDeliveryAvailabilityRequest.builder()
                .latitude(15.8300)
                .longitude(78.0400)
                .city(null) // Missing city
                .state("Andhra Pradesh")
                .build();

        assertThrows(InvalidRequestException.class, () -> deliveryZoneService.checkDeliveryAvailability(request));
    }

    @Test
    @DisplayName("12. Backward compatibility: Coordinate-only request validates via polygon across all active zones")
    void testLegacyCoordinateOnly_Success() {
        DeliveryZone activeZone = DeliveryZone.builder()
                .id(1L)
                .publicId("zone-uuid-1")
                .zoneName("Kurnool Zone 1")
                .boundaryGeojson(ZONE_1_GEOJSON)
                .active(true)
                .deleted(false)
                .build();

        when(deliveryZoneRepository.findByActiveTrueAndDeletedFalse()).thenReturn(List.of(activeZone));

        DeliveryAvailabilityResponse result = deliveryZoneService.checkDeliveryAvailability(15.8300, 78.0400, "Near Clock Tower");

        assertTrue(result.isAvailable());
        assertEquals("zone-uuid-1", result.getZonePublicId());
    }

    @Test
    @DisplayName("13. Inactive or deleted zones are not returned for delivery availability")
    void testInactiveOrDeletedZonesIgnored() {
        when(deliveryZoneRepository.findByActiveTrueAndDeletedFalse()).thenReturn(Collections.emptyList());

        DeliveryAvailabilityResponse result = deliveryZoneService.checkDeliveryAvailability(15.8300, 78.0400, null);

        assertFalse(result.isAvailable());
        assertNull(result.getZonePublicId());
    }

    @Test
    @DisplayName("14. Admin can update zone details and clear PIN code")
    void testUpdateZone_ClearPincode() {
        DeliveryZone zone = DeliveryZone.builder()
                .id(1L)
                .publicId("zone-uuid-1")
                .zoneName("Kurnool Zone 1")
                .zoneCode("KUR-ZONE-01")
                .city("Kurnool")
                .state("Andhra Pradesh")
                .pincode("518001")
                .pincodes(new HashSet<>(Set.of("518001")))
                .boundaryGeojson(ZONE_1_GEOJSON)
                .deliveryCharge(new BigDecimal("30.00"))
                .minimumOrderAmount(new BigDecimal("199.00"))
                .active(true)
                .deleted(false)
                .build();

        when(deliveryZoneRepository.findByPublicIdAndDeletedFalse("zone-uuid-1")).thenReturn(Optional.of(zone));
        when(deliveryZoneRepository.save(any(DeliveryZone.class))).thenAnswer(i -> i.getArgument(0));

        UpdateDeliveryZoneRequest updateRequest = UpdateDeliveryZoneRequest.builder()
                .zoneName("Kurnool Zone 1 Updated")
                .city("Kurnool")
                .state("Andhra Pradesh")
                .deliveryCharge(new BigDecimal("35.00"))
                .minimumOrderAmount(new BigDecimal("250.00"))
                .clearPincode(true) // Clear PIN code
                .build();

        DeliveryZoneResponse response = deliveryZoneService.updateZone("zone-uuid-1", updateRequest, "admin-1");

        assertNotNull(response);
        assertEquals("Kurnool Zone 1 Updated", response.getZoneName());
        assertNull(response.getPincode());
        assertTrue(response.getPincodes() == null || response.getPincodes().isEmpty());
    }

    @Test
    @DisplayName("15. Soft delete deactivates and marks zone as deleted")
    void testDeleteZone_SoftDeletes() {
        DeliveryZone zone = DeliveryZone.builder()
                .id(1L)
                .publicId("zone-uuid-1")
                .zoneName("Kurnool Zone 1")
                .active(true)
                .deleted(false)
                .build();

        when(deliveryZoneRepository.findByPublicIdAndDeletedFalse("zone-uuid-1")).thenReturn(Optional.of(zone));
        when(deliveryZoneRepository.save(any(DeliveryZone.class))).thenAnswer(i -> i.getArgument(0));

        deliveryZoneService.deleteZone("zone-uuid-1");

        assertFalse(zone.getActive());
        assertTrue(zone.getDeleted());
        assertNotNull(zone.getDeletedAt());
        verify(deliveryZoneRepository).save(zone);
    }
}
