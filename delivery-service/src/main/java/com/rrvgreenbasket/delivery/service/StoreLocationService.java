package com.rrvgreenbasket.delivery.service;

import com.rrvgreenbasket.delivery.dto.request.UpdateStoreSettingsRequest;
import com.rrvgreenbasket.delivery.dto.response.StoreLocationResponse;
import com.rrvgreenbasket.delivery.entity.StoreLocation;
import com.rrvgreenbasket.delivery.exception.StoreLocationNotConfiguredException;
import com.rrvgreenbasket.delivery.repository.StoreLocationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
@RequiredArgsConstructor
public class StoreLocationService {

    private final StoreLocationRepository storeLocationRepository;
    private final AtomicReference<StoreLocation> cachedPrimaryStore = new AtomicReference<>();

    /**
     * Retrieves the active primary store location entity.
     * Uses atomic reference cache for high performance.
     */
    @Transactional(readOnly = true)
    public StoreLocation getPrimaryStore() {
        StoreLocation cached = cachedPrimaryStore.get();
        if (cached != null) {
            return cached;
        }

        StoreLocation store = storeLocationRepository.findByPrimaryStoreTrueAndDeliveryEnabledTrue()
                .or(storeLocationRepository::findByPrimaryStoreTrue)
                .orElseThrow(() -> new StoreLocationNotConfiguredException(
                        "Primary store location is not configured in the database. Please initialize store settings."));

        cachedPrimaryStore.set(store);
        return store;
    }

    /**
     * Returns the safe public store location response DTO.
     */
    @Transactional(readOnly = true)
    public StoreLocationResponse getStoreLocationResponse() {
        StoreLocation store = getPrimaryStore();
        return mapToResponse(store);
    }

    /**
     * Updates store delivery settings and primary coordinates.
     */
    @Transactional
    public StoreLocationResponse updateStoreSettings(UpdateStoreSettingsRequest request, String adminUserId) {
        log.info("Admin '{}' updating store settings: name='{}', maxDistMeters={}",
                adminUserId, request.getStoreName(), request.getMaximumDeliveryDistanceMeters());

        StoreLocation store = storeLocationRepository.findByPrimaryStoreTrue()
                .orElseGet(() -> StoreLocation.builder()
                        .publicId("store-primary-" + java.util.UUID.randomUUID().toString().substring(0, 8))
                        .primaryStore(true)
                        .createdBy(adminUserId)
                        .build());

        store.setStoreName(request.getStoreName().trim());
        store.setFormattedAddress(request.getFormattedAddress().trim());
        if (request.getGooglePlaceId() != null && !request.getGooglePlaceId().isBlank()) {
            store.setGooglePlaceId(request.getGooglePlaceId().trim());
        }
        store.setLatitude(request.getLatitude());
        store.setLongitude(request.getLongitude());
        store.setCity(request.getCity().trim());
        store.setState(request.getState().trim());
        store.setPostalCode(request.getPostalCode().trim());
        store.setCountry(request.getCountry() != null && !request.getCountry().isBlank() ? request.getCountry().trim() : "India");
        store.setMaximumDeliveryDistanceMeters(request.getMaximumDeliveryDistanceMeters());
        store.setDeliveryEnabled(request.getDeliveryEnabled());
        store.setMinimumOrderAmount(request.getMinimumOrderAmount() != null ? request.getMinimumOrderAmount() : BigDecimal.ZERO);
        store.setFreeDeliveryThreshold(request.getFreeDeliveryThreshold());
        store.setEstimatedDeliveryTime(request.getEstimatedDeliveryTime() != null ? request.getEstimatedDeliveryTime().trim() : "30-45 mins");
        store.setCurrency(request.getCurrency() != null ? request.getCurrency().trim() : "INR");
        store.setUpdatedBy(adminUserId);

        StoreLocation saved = storeLocationRepository.save(store);
        cachedPrimaryStore.set(saved);
        log.info("Primary store location updated successfully: storeId={}", saved.getPublicId());
        return mapToResponse(saved);
    }

    /**
     * Enables or disables delivery acceptance globally for the primary store.
     */
    @Transactional
    public StoreLocationResponse updateDeliveryStatus(boolean enabled, String adminUserId) {
        log.info("Admin '{}' setting delivery enabled status to: {}", adminUserId, enabled);
        StoreLocation store = getPrimaryStore();
        store.setDeliveryEnabled(enabled);
        store.setUpdatedBy(adminUserId);

        StoreLocation saved = storeLocationRepository.save(store);
        cachedPrimaryStore.set(saved);
        return mapToResponse(saved);
    }

    public void invalidateCache() {
        cachedPrimaryStore.set(null);
    }

    private StoreLocationResponse mapToResponse(StoreLocation store) {
        double maxDistKm = BigDecimal.valueOf(store.getMaximumDeliveryDistanceMeters())
                .divide(BigDecimal.valueOf(1000), 2, RoundingMode.HALF_UP)
                .doubleValue();

        return StoreLocationResponse.builder()
                .storeId(store.getPublicId())
                .storeName(store.getStoreName())
                .googlePlaceId(store.getGooglePlaceId())
                .formattedAddress(store.getFormattedAddress())
                .latitude(store.getLatitude())
                .longitude(store.getLongitude())
                .city(store.getCity())
                .state(store.getState())
                .postalCode(store.getPostalCode())
                .country(store.getCountry())
                .maximumDeliveryDistanceKm(maxDistKm)
                .maximumDeliveryDistanceMeters(store.getMaximumDeliveryDistanceMeters())
                .deliveryEnabled(store.getDeliveryEnabled())
                .primaryStore(store.getPrimaryStore())
                .currency(store.getCurrency())
                .minimumOrderAmount(store.getMinimumOrderAmount())
                .freeDeliveryThreshold(store.getFreeDeliveryThreshold())
                .estimatedDeliveryTime(store.getEstimatedDeliveryTime())
                .build();
    }
}
