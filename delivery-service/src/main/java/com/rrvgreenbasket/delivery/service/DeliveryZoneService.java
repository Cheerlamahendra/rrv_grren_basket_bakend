package com.rrvgreenbasket.delivery.service;

import com.rrvgreenbasket.delivery.dto.request.CreateDeliveryZoneRequest;
import com.rrvgreenbasket.delivery.dto.request.UpdateDeliveryZoneRequest;
import com.rrvgreenbasket.delivery.dto.response.CustomerDeliveryZoneResponse;
import com.rrvgreenbasket.delivery.dto.response.DeliveryAvailabilityResponse;
import com.rrvgreenbasket.delivery.dto.response.DeliveryZoneResponse;
import com.rrvgreenbasket.delivery.dto.response.PageResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface DeliveryZoneService {

    DeliveryZoneResponse createZone(CreateDeliveryZoneRequest request, String adminUserId);

    DeliveryZoneResponse updateZone(String publicId, UpdateDeliveryZoneRequest request, String adminUserId);

    DeliveryZoneResponse updateZoneStatus(String publicId, boolean active, String adminUserId);

    void deleteZone(String publicId);

    DeliveryZoneResponse getZoneById(String publicId);

    PageResponse<DeliveryZoneResponse> getAllZones(String search, Boolean active, String city, Pageable pageable);

    List<CustomerDeliveryZoneResponse> getActiveCustomerZones();

    DeliveryAvailabilityResponse checkDeliveryAvailability(Double latitude, Double longitude, String address);

    DeliveryAvailabilityResponse checkDeliveryAvailability(com.rrvgreenbasket.delivery.dto.request.CheckDeliveryAvailabilityRequest request);

    DeliveryAvailabilityResponse resolveZoneByCoordinates(Double latitude, Double longitude);
}
