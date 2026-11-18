package com.rrvgreenbasket.delivery.service;

import com.rrvgreenbasket.delivery.dto.request.ValidateCheckoutRequest;
import com.rrvgreenbasket.delivery.dto.response.CheckoutValidationResponse;
import com.rrvgreenbasket.delivery.dto.response.VerifiedAddressResponse;
import com.rrvgreenbasket.delivery.entity.DeliveryPricingSlab;
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
public class DeliveryCheckoutValidationService {

    private final StoreLocationService storeLocationService;
    private final GoogleRoutesService googleRoutesService;
    private final DeliveryPricingService deliveryPricingService;
    private final AddressVerificationService addressVerificationService;

    /**
     * Revalidates customer delivery address, recalculates fresh road distance via Routes API,
     * checks delivery geofence, and calculates authoritative delivery fee at checkout time.
     *
     * @param request ValidateCheckoutRequest
     * @return CheckoutValidationResponse
     */
    public CheckoutValidationResponse validateCheckout(ValidateCheckoutRequest request) {
        if (request == null) {
            throw new InvalidRequestException("Checkout validation request cannot be null");
        }

        double destLat;
        double destLng;
        String formattedAddress;
        String placeId = request.getPlaceId();
        boolean verified = false;

        if (request.hasPlaceId()) {
            VerifiedAddressResponse verifiedResponse = addressVerificationService.verifyPlaceId(placeId.trim());
            destLat = verifiedResponse.getLatitude();
            destLng = verifiedResponse.getLongitude();
            formattedAddress = verifiedResponse.getFormattedAddress();
            verified = true;
        } else if (request.hasCoordinates()) {
            destLat = request.getLatitude();
            destLng = request.getLongitude();
            formattedAddress = request.getAddress() != null ? request.getAddress() : "Custom Coordinates";
        } else {
            throw new InvalidRequestException("Either a valid Google Place ID or coordinates (latitude, longitude) must be provided");
        }

        StoreLocation store = storeLocationService.getPrimaryStore();

        if (!Boolean.TRUE.equals(store.getDeliveryEnabled())) {
            log.warn("Checkout validation failed: Delivery service is currently disabled for store: {}", store.getStoreName());
            return CheckoutValidationResponse.builder()
                    .valid(false)
                    .available(false)
                    .verified(verified)
                    .cartId(request.getCartId())
                    .storeId(store.getPublicId())
                    .storeName(store.getStoreName())
                    .placeId(placeId)
                    .deliveryAddress(formattedAddress)
                    .latitude(destLat)
                    .longitude(destLng)
                    .currency(store.getCurrency())
                    .message("Delivery service is currently disabled by store management")
                    .build();
        }

        // Recompute actual driving road distance via Google Routes API
        RouteResult route = googleRoutesService.computeRoute(
                store.getLatitude(),
                store.getLongitude(),
                destLat,
                destLng
        );

        int actualDistanceMeters = route.distanceMeters();
        double actualDistanceKm = route.distanceKm();
        int maxAllowedMeters = store.getMaximumDeliveryDistanceMeters();

        if (actualDistanceMeters > maxAllowedMeters) {
            double maxDistKm = BigDecimal.valueOf(maxAllowedMeters)
                    .divide(BigDecimal.valueOf(1000), 2, RoundingMode.HALF_UP)
                    .doubleValue();

            log.warn("Checkout validation failed: road distance {} m exceeds max allowed {} m",
                    actualDistanceMeters, maxAllowedMeters);

            return CheckoutValidationResponse.builder()
                    .valid(false)
                    .available(false)
                    .verified(verified)
                    .cartId(request.getCartId())
                    .storeId(store.getPublicId())
                    .storeName(store.getStoreName())
                    .placeId(placeId)
                    .deliveryAddress(formattedAddress)
                    .latitude(destLat)
                    .longitude(destLng)
                    .distanceMeters(actualDistanceMeters)
                    .distanceKm(actualDistanceKm)
                    .estimatedTravelDuration(route.duration())
                    .currency(store.getCurrency())
                    .minimumOrderAmount(store.getMinimumOrderAmount())
                    .freeDeliveryThreshold(store.getFreeDeliveryThreshold())
                    .message(String.format("Delivery address is outside our maximum serviceable range of %s km (actual road distance: %s km)",
                            maxDistKm, actualDistanceKm))
                    .build();
        }

        // Retrieve current authoritative delivery pricing slab
        DeliveryPricingSlab matchedSlab = deliveryPricingService.findSlabForDistance(actualDistanceMeters);
        BigDecimal deliveryCharge = deliveryPricingService.calculateDeliveryCharge(actualDistanceMeters);

        log.info("Checkout validation SUCCESS: distance={} m, charge=₹{}, slabId={}",
                actualDistanceMeters, deliveryCharge, matchedSlab != null ? matchedSlab.getId() : null);

        return CheckoutValidationResponse.builder()
                .valid(true)
                .available(true)
                .verified(verified)
                .cartId(request.getCartId())
                .storeId(store.getPublicId())
                .storeName(store.getStoreName())
                .placeId(placeId)
                .deliveryAddress(formattedAddress)
                .latitude(destLat)
                .longitude(destLng)
                .distanceMeters(actualDistanceMeters)
                .distanceKm(actualDistanceKm)
                .estimatedTravelDuration(route.duration())
                .deliveryCharge(deliveryCharge)
                .currency(store.getCurrency())
                .minimumOrderAmount(store.getMinimumOrderAmount())
                .freeDeliveryThreshold(store.getFreeDeliveryThreshold())
                .pricingSlabId(matchedSlab != null ? matchedSlab.getId() : null)
                .message("Checkout delivery location and authoritative pricing validated successfully")
                .build();
    }
}
