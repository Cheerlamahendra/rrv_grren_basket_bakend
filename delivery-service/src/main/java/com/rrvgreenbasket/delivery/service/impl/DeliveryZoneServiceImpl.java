package com.rrvgreenbasket.delivery.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrvgreenbasket.delivery.dto.request.CheckDeliveryAvailabilityRequest;
import com.rrvgreenbasket.delivery.dto.request.CreateDeliveryZoneRequest;
import com.rrvgreenbasket.delivery.dto.request.UpdateDeliveryZoneRequest;
import com.rrvgreenbasket.delivery.dto.response.CustomerDeliveryZoneResponse;
import com.rrvgreenbasket.delivery.dto.response.DeliveryAvailabilityResponse;
import com.rrvgreenbasket.delivery.dto.response.DeliveryZoneResponse;
import com.rrvgreenbasket.delivery.dto.response.PageResponse;
import com.rrvgreenbasket.delivery.entity.DeliveryZone;
import com.rrvgreenbasket.delivery.exception.DuplicateResourceException;
import com.rrvgreenbasket.delivery.exception.InvalidRequestException;
import com.rrvgreenbasket.delivery.exception.ZoneNotFoundException;
import com.rrvgreenbasket.delivery.exception.ZoneOverlapException;
import com.rrvgreenbasket.delivery.geocoding.ReverseGeocodingService;
import com.rrvgreenbasket.delivery.geometry.GeometryService;
import com.rrvgreenbasket.delivery.repository.DeliveryZoneRepository;
import com.rrvgreenbasket.delivery.service.DeliveryZoneService;
import com.rrvgreenbasket.delivery.util.AddressNormalizer;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Geometry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryZoneServiceImpl implements DeliveryZoneService {

    private final DeliveryZoneRepository deliveryZoneRepository;
    private final GeometryService geometryService;
    private final ReverseGeocodingService reverseGeocodingService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public DeliveryZoneResponse createZone(CreateDeliveryZoneRequest request, String adminUserId) {
        log.info("Admin '{}' creating delivery zone: '{}' ({})", adminUserId, request.getZoneName(), request.getZoneCode());

        String cleanCode = request.getZoneCode().trim().toUpperCase();
        if (deliveryZoneRepository.existsByZoneCodeAndDeletedFalse(cleanCode) ||
                deliveryZoneRepository.existsByZoneCode(cleanCode)) {
            throw new DuplicateResourceException("Zone code '" + request.getZoneCode() + "' is already in use");
        }

        validateMonetaryValues(request.getDeliveryCharge(), request.getMinimumOrderAmount(), request.getFreeDeliveryThreshold());

        // Parse and validate geometry (Polygon or MultiPolygon)
        Geometry geometry = geometryService.parseAndValidateJsonNodeToGeometry(request.getBoundary());
        String canonicalGeoJson = geometryService.toCanonicalGeoJson(geometry);

        // Normalize city & state
        String cleanCity = AddressNormalizer.toTitleCase(request.getCity());
        String cleanState = AddressNormalizer.toTitleCase(request.getState());

        // Overlap verification with existing active zones in the same city/state
        if (Boolean.TRUE.equals(request.getActive())) {
            checkZoneOverlap(geometry, null, cleanCity, cleanState);
        }

        // Pincode setup
        String primaryPin = AddressNormalizer.normalizePincode(request.getPincode());
        Set<String> pinSet = new HashSet<>();
        if (primaryPin != null) {
            pinSet.add(primaryPin);
        }
        if (request.getPincodes() != null) {
            for (String p : request.getPincodes()) {
                String cleanP = AddressNormalizer.normalizePincode(p);
                if (cleanP != null) {
                    pinSet.add(cleanP);
                }
            }
        }

        DeliveryZone zone = DeliveryZone.builder()
                .publicId(UUID.randomUUID().toString())
                .zoneName(request.getZoneName().trim())
                .zoneCode(cleanCode)
                .description(request.getDescription())
                .city(cleanCity)
                .state(cleanState)
                .pincode(primaryPin)
                .pincodes(pinSet)
                .boundaryGeojson(canonicalGeoJson)
                .deliveryCharge(request.getDeliveryCharge())
                .minimumOrderAmount(request.getMinimumOrderAmount())
                .freeDeliveryThreshold(request.getFreeDeliveryThreshold())
                .estimatedDeliveryTime(request.getEstimatedDeliveryTime() != null ? request.getEstimatedDeliveryTime().trim() : "NEXT_DAY")
                .active(request.getActive() != null ? request.getActive() : true)
                .deleted(false)
                .createdBy(adminUserId)
                .updatedBy(adminUserId)
                .build();

        DeliveryZone saved = deliveryZoneRepository.save(zone);
        geometryService.invalidateCache();
        log.info("Delivery zone created successfully: id={}, publicId={}, name={}", saved.getId(), saved.getPublicId(), saved.getZoneName());
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public DeliveryZoneResponse updateZone(String publicId, UpdateDeliveryZoneRequest request, String adminUserId) {
        log.info("Admin '{}' updating delivery zone: {}", adminUserId, publicId);

        DeliveryZone zone = deliveryZoneRepository.findByPublicIdAndDeletedFalse(publicId)
                .or(() -> deliveryZoneRepository.findByPublicId(publicId))
                .orElseThrow(() -> new ZoneNotFoundException("Delivery zone not found with public ID: " + publicId));

        if (Boolean.TRUE.equals(zone.getDeleted())) {
            throw new ZoneNotFoundException("Delivery zone with public ID " + publicId + " is deleted and cannot be modified");
        }

        if (request.getZoneCode() != null && !request.getZoneCode().equalsIgnoreCase(zone.getZoneCode())) {
            String cleanCode = request.getZoneCode().trim().toUpperCase();
            if (deliveryZoneRepository.existsByZoneCodeAndPublicIdNotAndDeletedFalse(cleanCode, publicId)) {
                throw new DuplicateResourceException("Zone code '" + request.getZoneCode() + "' is already taken");
            }
            zone.setZoneCode(cleanCode);
        }

        validateMonetaryValues(request.getDeliveryCharge(), request.getMinimumOrderAmount(), request.getFreeDeliveryThreshold());

        zone.setZoneName(request.getZoneName().trim());
        zone.setDescription(request.getDescription());
        String cleanCity = AddressNormalizer.toTitleCase(request.getCity());
        String cleanState = AddressNormalizer.toTitleCase(request.getState());
        zone.setCity(cleanCity);
        zone.setState(cleanState);

        // PIN code update / clearing handling
        if (Boolean.TRUE.equals(request.getClearPincode())) {
            zone.setPincode(null);
            zone.getPincodes().clear();
        } else {
            if (request.getPincode() != null) {
                if (request.getPincode().isBlank()) {
                    zone.setPincode(null);
                } else {
                    zone.setPincode(AddressNormalizer.normalizePincode(request.getPincode()));
                }
            }
            if (request.getPincodes() != null) {
                zone.getPincodes().clear();
                for (String p : request.getPincodes()) {
                    String cleanP = AddressNormalizer.normalizePincode(p);
                    if (cleanP != null) {
                        zone.getPincodes().add(cleanP);
                    }
                }
            }
        }

        zone.setDeliveryCharge(request.getDeliveryCharge());
        zone.setMinimumOrderAmount(request.getMinimumOrderAmount());
        zone.setFreeDeliveryThreshold(request.getFreeDeliveryThreshold());
        if (request.getEstimatedDeliveryTime() != null) {
            zone.setEstimatedDeliveryTime(request.getEstimatedDeliveryTime().trim());
        }

        if (request.getActive() != null) {
            zone.setActive(request.getActive());
        }

        if (request.getBoundary() != null) {
            Geometry geometry = geometryService.parseAndValidateJsonNodeToGeometry(request.getBoundary());
            if (Boolean.TRUE.equals(zone.getActive())) {
                checkZoneOverlap(geometry, zone.getId(), cleanCity, cleanState);
            }
            zone.setBoundaryGeojson(geometryService.toCanonicalGeoJson(geometry));
        } else if (Boolean.TRUE.equals(zone.getActive())) {
            // If activating without changing boundary, re-verify overlap against other active zones
            Geometry currentGeom = geometryService.parseAndValidateGeometry(zone.getBoundaryGeojson());
            checkZoneOverlap(currentGeom, zone.getId(), cleanCity, cleanState);
        }

        zone.setUpdatedBy(adminUserId);
        DeliveryZone updated = deliveryZoneRepository.save(zone);
        geometryService.invalidateCache();
        log.info("Delivery zone '{}' updated successfully", publicId);
        return mapToResponse(updated);
    }

    @Override
    @Transactional
    public DeliveryZoneResponse updateZoneStatus(String publicId, boolean active, String adminUserId) {
        log.info("Admin '{}' updating delivery zone '{}' status to active={}", adminUserId, publicId, active);

        DeliveryZone zone = deliveryZoneRepository.findByPublicIdAndDeletedFalse(publicId)
                .or(() -> deliveryZoneRepository.findByPublicId(publicId))
                .orElseThrow(() -> new ZoneNotFoundException("Delivery zone not found with public ID: " + publicId));

        if (Boolean.TRUE.equals(zone.getDeleted())) {
            throw new ZoneNotFoundException("Delivery zone with public ID " + publicId + " is deleted");
        }

        if (active) {
            Geometry geometry = geometryService.parseAndValidateGeometry(zone.getBoundaryGeojson());
            checkZoneOverlap(geometry, zone.getId(), zone.getCity(), zone.getState());
        }

        zone.setActive(active);
        zone.setUpdatedBy(adminUserId);
        DeliveryZone updated = deliveryZoneRepository.save(zone);
        geometryService.invalidateCache();
        return mapToResponse(updated);
    }

    @Override
    @Transactional
    public void deleteZone(String publicId) {
        log.info("Soft-deleting delivery zone: {}", publicId);
        DeliveryZone zone = deliveryZoneRepository.findByPublicIdAndDeletedFalse(publicId)
                .or(() -> deliveryZoneRepository.findByPublicId(publicId))
                .orElseThrow(() -> new ZoneNotFoundException("Delivery zone not found with public ID: " + publicId));

        // Soft-delete to preserve historical order snapshots
        zone.setActive(false);
        zone.setDeleted(true);
        zone.setDeletedAt(LocalDateTime.now());
        deliveryZoneRepository.save(zone);
        geometryService.invalidateCache();
    }

    @Override
    @Transactional(readOnly = true)
    public DeliveryZoneResponse getZoneById(String publicId) {
        return deliveryZoneRepository.findByPublicIdAndDeletedFalse(publicId)
                .or(() -> deliveryZoneRepository.findByPublicId(publicId))
                .map(this::mapToResponse)
                .orElseThrow(() -> new ZoneNotFoundException("Delivery zone not found with public ID: " + publicId));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<DeliveryZoneResponse> getAllZones(String search, Boolean active, String city, Pageable pageable) {
        Specification<DeliveryZone> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Exclude soft-deleted zones by default
            predicates.add(cb.equal(root.get("deleted"), false));

            if (active != null) {
                predicates.add(cb.equal(root.get("active"), active));
            }
            if (city != null && !city.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("city")), "%" + city.trim().toLowerCase() + "%"));
            }
            if (search != null && !search.isBlank()) {
                String term = "%" + search.trim().toLowerCase() + "%";
                Predicate nameMatch = cb.like(cb.lower(root.get("zoneName")), term);
                Predicate codeMatch = cb.like(cb.lower(root.get("zoneCode")), term);
                Predicate descMatch = cb.like(cb.lower(root.get("description")), term);
                Predicate cityMatch = cb.like(cb.lower(root.get("city")), term);
                Predicate pinMatch = cb.like(cb.lower(root.get("pincode")), term);
                predicates.add(cb.or(nameMatch, codeMatch, descMatch, cityMatch, pinMatch));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<DeliveryZone> page = deliveryZoneRepository.findAll(spec, pageable);
        return PageResponse.from(page.map(this::mapToResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerDeliveryZoneResponse> getActiveCustomerZones() {
        return deliveryZoneRepository.findByActiveTrueAndDeletedFalse().stream()
                .map(z -> CustomerDeliveryZoneResponse.builder()
                        .publicId(z.getPublicId())
                        .zoneName(z.getZoneName())
                        .city(z.getCity())
                        .state(z.getState())
                        .pincode(z.getPincode())
                        .pincodes(z.getAllPincodes())
                        .boundary(z.getBoundaryGeojson())
                        .deliveryCharge(z.getDeliveryCharge())
                        .minimumOrderAmount(z.getMinimumOrderAmount())
                        .freeDeliveryThreshold(z.getFreeDeliveryThreshold())
                        .estimatedDeliveryTime(z.getEstimatedDeliveryTime())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public DeliveryAvailabilityResponse checkDeliveryAvailability(Double latitude, Double longitude, String address) {
        return checkDeliveryAvailability(CheckDeliveryAvailabilityRequest.builder()
                .latitude(latitude)
                .longitude(longitude)
                .address(address)
                .build());
    }

    @Override
    @Transactional(readOnly = true)
    public DeliveryAvailabilityResponse checkDeliveryAvailability(CheckDeliveryAvailabilityRequest request) {
        Double latitude = request.getLatitude();
        Double longitude = request.getLongitude();

        if (latitude == null || longitude == null) {
            throw new InvalidRequestException("Latitude and longitude are required to verify delivery availability");
        }

        // 1. Validate latitude and longitude range
        geometryService.validateCoordinateRange(latitude, longitude);

        String rawCity = request.getCity();
        String rawState = request.getState();
        String rawPin = request.getPincode();

        boolean hasCity = rawCity != null && !rawCity.isBlank();
        boolean hasState = rawState != null && !rawState.isBlank();

        // City and state must be supplied together rule
        if (hasCity && !hasState) {
            throw new InvalidRequestException("City and state must be supplied together");
        }
        if (!hasCity && hasState) {
            throw new InvalidRequestException("City and state must be supplied together");
        }

        boolean isHybridValidation = hasCity && hasState;

        // 2 & 3. Normalize city, state, PIN code
        String cleanCity = isHybridValidation ? AddressNormalizer.normalizeCanonical(rawCity) : null;
        String cleanState = isHybridValidation ? AddressNormalizer.normalizeCanonical(rawState) : null;
        String cleanPin = AddressNormalizer.normalizePincode(rawPin);

        // Address consistency check via reverse geocoding if configured
        String consistencyWarning = reverseGeocodingService.verifyAddressConsistency(
                latitude, longitude, rawCity, rawState, cleanPin);
        if (consistencyWarning != null) {
            return DeliveryAvailabilityResponse.builder()
                    .available(false)
                    .message(consistencyWarning)
                    .build();
        }

        List<DeliveryZone> candidateZones;

        if (isHybridValidation) {
            // 4. Find active, non-deleted zones matching city and state
            candidateZones = deliveryZoneRepository.findByCityIgnoreCaseAndStateIgnoreCaseAndActiveTrueAndDeletedFalse(
                    cleanCity, cleanState);

            if (candidateZones.isEmpty()) {
                log.info("No active delivery zones configured for city='{}', state='{}'", rawCity, rawState);
                return DeliveryAvailabilityResponse.builder()
                        .available(false)
                        .zonePublicId(null)
                        .zoneName(null)
                        .message("Delivery is currently not available in " + rawCity.trim() + ", " + rawState.trim())
                        .build();
            }
        } else {
            // Backward compatibility for legacy coordinate-only requests: search across all active zones
            candidateZones = deliveryZoneRepository.findByActiveTrueAndDeletedFalse();
            if (candidateZones.isEmpty()) {
                candidateZones = deliveryZoneRepository.findByActiveTrue();
            }
        }

        boolean pinMismatchDetected = false;
        String matchedZoneNameForPinMismatch = null;

        for (DeliveryZone zone : candidateZones) {
            // If hybrid validation with PIN restriction:
            if (isHybridValidation && zone.hasPincodeRestriction()) {
                // 5. Validate PIN code when the zone has a configured PIN code
                if (cleanPin == null || !zone.supportsPincode(cleanPin)) {
                    // Check if coordinates would have matched, to provide informative message
                    try {
                        Geometry geom = geometryService.getOrParseGeometry(zone.getBoundaryGeojson());
                        if (geometryService.isPointInsideGeometry(latitude, longitude, geom)) {
                            pinMismatchDetected = true;
                            matchedZoneNameForPinMismatch = zone.getZoneName();
                        }
                    } catch (Exception ignored) {}
                    continue;
                }
            }

            // 6. Validate customer coordinates against configured polygon
            try {
                Geometry geom = geometryService.getOrParseGeometry(zone.getBoundaryGeojson());
                if (geometryService.isPointInsideGeometry(latitude, longitude, geom)) {
                    log.info("Point [lat={}, lng={}] matched active zone '{}' ({}) via {} validation",
                            latitude, longitude, zone.getZoneName(), zone.getPublicId(),
                            isHybridValidation ? "HYBRID" : "COORDINATES_ONLY");

                    // 7 & 8. Return delivery availability and matching zone's delivery configuration
                    return DeliveryAvailabilityResponse.builder()
                            .available(true)
                            .zonePublicId(zone.getPublicId())
                            .zoneName(zone.getZoneName())
                            .deliveryCharge(zone.getDeliveryCharge())
                            .minimumOrderAmount(zone.getMinimumOrderAmount())
                            .freeDeliveryThreshold(zone.getFreeDeliveryThreshold())
                            .estimatedDeliveryTime(zone.getEstimatedDeliveryTime())
                            .message("Delivery is available in your selected area")
                            .build();
                }
            } catch (Exception e) {
                log.error("Failed to evaluate geometry for zone '{}' (id={}): {}", zone.getZoneName(), zone.getId(), e.getMessage());
            }
        }

        if (pinMismatchDetected) {
            log.info("Delivery unavailable: PIN code {} not serviced in zone '{}'", cleanPin, matchedZoneNameForPinMismatch);
            return DeliveryAvailabilityResponse.builder()
                    .available(false)
                    .zonePublicId(null)
                    .zoneName(null)
                    .message("Delivery is not available to PIN code " + (cleanPin != null ? cleanPin : "provided") +
                            " in " + rawCity.trim())
                    .build();
        }

        log.info("Point [lat={}, lng={}] is outside all active delivery zone boundaries", latitude, longitude);
        return DeliveryAvailabilityResponse.builder()
                .available(false)
                .zonePublicId(null)
                .zoneName(null)
                .message("Delivery is not available in your selected area")
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public DeliveryAvailabilityResponse resolveZoneByCoordinates(Double latitude, Double longitude) {
        return checkDeliveryAvailability(latitude, longitude, null);
    }

    private void checkZoneOverlap(Geometry newGeometry, Long excludeZoneId, String city, String state) {
        List<DeliveryZone> activeZones = deliveryZoneRepository.findByActiveTrueAndDeletedFalse();
        for (DeliveryZone existing : activeZones) {
            if (excludeZoneId != null && existing.getId().equals(excludeZoneId)) {
                continue;
            }

            // Only zones in the same city/state can geometrically overlap in practice
            if (city != null && state != null &&
                    (!AddressNormalizer.matches(city, existing.getCity()) || !AddressNormalizer.matches(state, existing.getState()))) {
                continue;
            }

            Geometry existingGeom = geometryService.getOrParseGeometry(existing.getBoundaryGeojson());
            if (geometryService.doGeometriesOverlap(newGeometry, existingGeom)) {
                log.warn("Spatial overlap detected with existing zone: '{}' ({})", existing.getZoneName(), existing.getZoneCode());
                throw new ZoneOverlapException("Delivery zone polygon overlaps with active delivery zone '" +
                        existing.getZoneName() + "' (" + existing.getZoneCode() + ")");
            }
        }
    }

    private void validateMonetaryValues(BigDecimal deliveryCharge, BigDecimal minimumOrderAmount, BigDecimal freeDeliveryThreshold) {
        if (deliveryCharge != null && deliveryCharge.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidRequestException("Delivery charge cannot be negative");
        }
        if (minimumOrderAmount != null && minimumOrderAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidRequestException("Minimum order amount cannot be negative");
        }
        if (freeDeliveryThreshold != null && freeDeliveryThreshold.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidRequestException("Free delivery threshold cannot be negative");
        }
    }

    private DeliveryZoneResponse mapToResponse(DeliveryZone zone) {
        return DeliveryZoneResponse.builder()
                .publicId(zone.getPublicId())
                .zoneName(zone.getZoneName())
                .zoneCode(zone.getZoneCode())
                .description(zone.getDescription())
                .city(zone.getCity())
                .state(zone.getState())
                .pincode(zone.getPincode())
                .pincodes(zone.getAllPincodes())
                .boundary(zone.getBoundaryGeojson())
                .deliveryCharge(zone.getDeliveryCharge())
                .minimumOrderAmount(zone.getMinimumOrderAmount())
                .freeDeliveryThreshold(zone.getFreeDeliveryThreshold())
                .estimatedDeliveryTime(zone.getEstimatedDeliveryTime())
                .active(Boolean.TRUE.equals(zone.getActive()))
                .deleted(Boolean.TRUE.equals(zone.getDeleted()))
                .createdAt(zone.getCreatedAt())
                .updatedAt(zone.getUpdatedAt())
                .createdBy(zone.getCreatedBy())
                .updatedBy(zone.getUpdatedBy())
                .build();
    }
}
