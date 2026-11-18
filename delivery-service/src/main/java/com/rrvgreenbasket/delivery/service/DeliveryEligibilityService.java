package com.rrvgreenbasket.delivery.service;

import com.rrvgreenbasket.delivery.dto.request.CheckDeliveryAvailabilityRequest;
import com.rrvgreenbasket.delivery.dto.response.DeliveryAvailabilityResponse;
import com.rrvgreenbasket.delivery.dto.response.ResolvedLocationResponse;
import com.rrvgreenbasket.delivery.dto.response.VerifiedAddressResponse;
import com.rrvgreenbasket.delivery.entity.StoreLocation;
import com.rrvgreenbasket.delivery.exception.DeliveryNotAvailableException;
import com.rrvgreenbasket.delivery.exception.InvalidRequestException;
import com.rrvgreenbasket.delivery.service.GoogleRoutesService.RouteResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryEligibilityService {

    private final StoreLocationService storeLocationService;
    private final GoogleRoutesService googleRoutesService;
    private final DeliveryPricingService deliveryPricingService;
    private final AddressVerificationService addressVerificationService;

    /**
     * Checks delivery availability given a request containing either a Google Place ID
     * or geographical coordinates.
     *
     * @param request CheckDeliveryAvailabilityRequest
     * @return DeliveryAvailabilityResponse
     */
    public DeliveryAvailabilityResponse checkAvailability(CheckDeliveryAvailabilityRequest request) {
        if (request == null) {
            throw new InvalidRequestException("Delivery availability request cannot be null");
        }

        if (request.hasPlaceId()) {
            return checkEligibilityByPlaceId(request.getPlaceId().trim());
        } else if (request.hasCoordinates()) {
            return checkEligibilityByCoordinates(request.getLatitude(), request.getLongitude(), request.getAddress());
        } else {
            throw new InvalidRequestException("Either a valid Google Place ID or coordinates (latitude, longitude) must be provided");
        }
    }

    /**
     * Checks delivery eligibility for a verified Google Place ID.
     */
    public DeliveryAvailabilityResponse checkEligibilityByPlaceId(String placeId) {
        VerifiedAddressResponse verifiedAddress = addressVerificationService.verifyPlaceId(placeId);
        return evaluateEligibility(
                verifiedAddress.getLatitude(),
                verifiedAddress.getLongitude(),
                verifiedAddress.getFormattedAddress(),
                true
        );
    }

    /**
     * Checks delivery eligibility for coordinates (used by Order Service and legacy callers).
     */
    public DeliveryAvailabilityResponse checkEligibilityByCoordinates(Double latitude, Double longitude, String address) {
        return evaluateEligibility(latitude, longitude, address, false);
    }

    /**
     * Enriches a resolved location with delivery eligibility and pricing.
     */
    public ResolvedLocationResponse enrichResolvedLocationWithDelivery(ResolvedLocationResponse location) {
        try {
            DeliveryAvailabilityResponse availability = evaluateEligibility(
                    location.getLatitude(),
                    location.getLongitude(),
                    location.getFormattedAddress(),
                    true
            );

            location.setAvailableForDelivery(availability.isAvailable());
            location.setDistanceMeters(availability.getDistanceMeters());
            location.setDistanceKm(availability.getDistanceKm());
            location.setEstimatedTravelDuration(availability.getEstimatedTravelDuration());
            location.setDeliveryCharge(availability.getDeliveryCharge());
            location.setCurrency(availability.getCurrency());
            location.setMessage(availability.getMessage());

        } catch (Exception e) {
            log.warn("Could not compute route eligibility for resolved location: {}", e.getMessage());
            location.setAvailableForDelivery(false);
            location.setMessage("Location resolved, but delivery availability could not be verified: " + e.getMessage());
        }

        return location;
    }

    private DeliveryAvailabilityResponse evaluateEligibility(
            Double customerLat,
            Double customerLng,
            String customerAddress,
            boolean isPlaceVerified) {

        StoreLocation store = storeLocationService.getPrimaryStore();

        double maxDistKm = BigDecimal.valueOf(store.getMaximumDeliveryDistanceMeters())
                .divide(BigDecimal.valueOf(1000), 2, RoundingMode.HALF_UP)
                .doubleValue();

        // 1. Check if store has delivery acceptance enabled
        if (!Boolean.TRUE.equals(store.getDeliveryEnabled())) {
            log.info("Delivery is globally disabled for store '{}'", store.getStoreName());
            return DeliveryAvailabilityResponse.builder()
                    .available(false)
                    .verified(isPlaceVerified)
                    .maximumDeliveryDistanceKm(maxDistKm)
                    .currency(store.getCurrency())
                    .zonePublicId(store.getPublicId())
                    .zoneName(store.getStoreName())
                    .minimumOrderAmount(store.getMinimumOrderAmount())
                    .freeDeliveryThreshold(store.getFreeDeliveryThreshold())
                    .estimatedDeliveryTime(store.getEstimatedDeliveryTime())
                    .message("Delivery service is currently not accepting new orders")
                    .build();
        }

        // 2. Call Google Routes API to get actual driving road distance
        RouteResult route = googleRoutesService.computeRoute(
                store.getLatitude(),
                store.getLongitude(),
                customerLat,
                customerLng
        );

        int actualDistanceMeters = route.distanceMeters();
        double actualDistanceKm = route.distanceKm();
        int maxAllowedMeters = store.getMaximumDeliveryDistanceMeters();

        // 3. Compare actual distance in meters against configured maximum
        // 0 to maxAllowedMeters inclusive -> Available
        // Above maxAllowedMeters -> Unavailable
        if (actualDistanceMeters <= maxAllowedMeters) {
            BigDecimal deliveryCharge = deliveryPricingService.calculateDeliveryCharge(actualDistanceMeters);

            log.info("Delivery AVAILABLE: distance={} m (limit={} m), charge=₹{}",
                    actualDistanceMeters, maxAllowedMeters, deliveryCharge);

            return DeliveryAvailabilityResponse.builder()
                    .available(true)
                    .verified(isPlaceVerified)
                    .distanceMeters(actualDistanceMeters)
                    .distanceKm(actualDistanceKm)
                    .estimatedTravelDuration(route.duration())
                    .deliveryCharge(deliveryCharge)
                    .currency(store.getCurrency())
                    .maximumDeliveryDistanceKm(maxDistKm)
                    .zonePublicId(store.getPublicId())
                    .zoneName(store.getStoreName())
                    .minimumOrderAmount(store.getMinimumOrderAmount())
                    .freeDeliveryThreshold(store.getFreeDeliveryThreshold())
                    .estimatedDeliveryTime(store.getEstimatedDeliveryTime())
                    .message("Delivery is available at your location")
                    .build();
        } else {
            log.info("Delivery UNAVAILABLE: distance={} m exceeds limit={} m", actualDistanceMeters, maxAllowedMeters);

            return DeliveryAvailabilityResponse.builder()
                    .available(false)
                    .verified(isPlaceVerified)
                    .distanceMeters(actualDistanceMeters)
                    .distanceKm(actualDistanceKm)
                    .estimatedTravelDuration(route.duration())
                    .deliveryCharge(null)
                    .currency(store.getCurrency())
                    .maximumDeliveryDistanceKm(maxDistKm)
                    .zonePublicId(store.getPublicId())
                    .zoneName(store.getStoreName())
                    .minimumOrderAmount(store.getMinimumOrderAmount())
                    .freeDeliveryThreshold(store.getFreeDeliveryThreshold())
                    .estimatedDeliveryTime(store.getEstimatedDeliveryTime())
                    .message(String.format("Sorry, we currently deliver only within %s km of our store.", maxDistKm))
                    .build();
        }
    }
}
