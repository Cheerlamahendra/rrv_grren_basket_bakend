package com.rrvgreenbasket.delivery.controller;

import com.rrvgreenbasket.delivery.dto.request.CheckDeliveryAvailabilityRequest;
import com.rrvgreenbasket.delivery.dto.request.CustomerDeliveryAddressRequest;
import com.rrvgreenbasket.delivery.dto.request.ResolveLocationRequest;
import com.rrvgreenbasket.delivery.dto.request.ValidateCheckoutRequest;
import com.rrvgreenbasket.delivery.dto.request.VerifyAddressRequest;
import com.rrvgreenbasket.delivery.dto.response.*;
import com.rrvgreenbasket.delivery.security.UserPrincipal;
import com.rrvgreenbasket.delivery.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/delivery")
@RequiredArgsConstructor
@Tag(name = "Customer Delivery & Address Verification", description = "Endpoints for Google Places verification, store geofencing, dynamic delivery pricing, and checkout availability")
public class CustomerDeliveryController {

    private final StoreLocationService storeLocationService;
    private final AddressVerificationService addressVerificationService;
    private final DeliveryEligibilityService deliveryEligibilityService;
    private final DeliveryCheckoutValidationService checkoutValidationService;
    private final CustomerAddressService customerAddressService;
    private final DeliveryZoneService legacyDeliveryZoneService;

    @Operation(summary = "Get primary store location and delivery settings", description = "Returns active primary store coordinates, maximum delivery radius, and currency")
    @GetMapping(value = "/store-location", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<StoreLocationResponse>> getStoreLocation() {
        StoreLocationResponse response = storeLocationService.getStoreLocationResponse();
        return ResponseEntity.ok(ApiResponse.success("Store location retrieved successfully", response));
    }

    @Operation(summary = "Verify customer Google Place ID", description = "Verifies Place ID with Google Places API (New), validates India boundary and returns normalized address and coordinates")
    @PostMapping(value = "/verify-address", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<VerifiedAddressResponse>> verifyAddress(
            @Valid @RequestBody VerifyAddressRequest request) {

        VerifiedAddressResponse response = addressVerificationService.verifyPlaceId(request.getPlaceId());
        return ResponseEntity.ok(ApiResponse.success("Address verified successfully", response));
    }

    @Operation(summary = "Resolve customer current device location", description = "Reverse-geocodes customer GPS coordinates using Google Geocoding API and evaluates delivery availability")
    @PostMapping(value = "/resolve-location", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<ResolvedLocationResponse>> resolveLocation(
            @Valid @RequestBody ResolveLocationRequest request) {

        ResolvedLocationResponse location = addressVerificationService.resolveCurrentLocation(
                request.getLatitude(), request.getLongitude());
        ResolvedLocationResponse response = deliveryEligibilityService.enrichResolvedLocationWithDelivery(location);
        return ResponseEntity.ok(ApiResponse.success("Location resolved successfully", response));
    }

    @Operation(summary = "Check delivery availability and dynamic pricing", description = "Calculates actual road distance via Google Routes API and evaluates 5 km geofence with distance pricing slabs")
    @PostMapping(value = "/check-availability", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<DeliveryAvailabilityResponse>> checkAvailability(
            @Valid @RequestBody CheckDeliveryAvailabilityRequest request) {

        DeliveryAvailabilityResponse response = deliveryEligibilityService.checkAvailability(request);
        return ResponseEntity.ok(ApiResponse.success(response.getMessage(), response));
    }

    @Operation(summary = "Validate delivery at checkout", description = "Reverifies address, recalculates authoritative road distance, and produces immutable delivery charge for order checkout")
    @PostMapping(value = "/validate-checkout", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<CheckoutValidationResponse>> validateCheckout(
            @Valid @RequestBody ValidateCheckoutRequest request) {

        CheckoutValidationResponse response = checkoutValidationService.validateCheckout(request);
        return ResponseEntity.ok(ApiResponse.success(response.getMessage(), response));
    }

    // =========================================================================
    // Customer Address Book Endpoints (Authenticated)
    // =========================================================================

    @Operation(summary = "Get customer saved delivery addresses", description = "Retrieves verified saved addresses for the authenticated customer")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('CUSTOMER')")
    @GetMapping(value = "/customer-addresses", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<List<CustomerDeliveryAddressResponse>>> getCustomerAddresses(
            @AuthenticationPrincipal UserPrincipal principal) {

        String customerId = principal != null ? principal.getUserId() : "ANONYMOUS";
        List<CustomerDeliveryAddressResponse> addresses = customerAddressService.getCustomerAddresses(customerId);
        return ResponseEntity.ok(ApiResponse.success("Customer delivery addresses retrieved successfully", addresses));
    }

    @Operation(summary = "Save customer delivery address with verified Google Place ID", description = "Stores customer address with Google Place ID and verified authoritative coordinates")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('CUSTOMER')")
    @PostMapping(value = "/customer-addresses", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<CustomerDeliveryAddressResponse>> saveCustomerAddress(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CustomerDeliveryAddressRequest request) {

        String customerId = principal != null ? principal.getUserId() : "ANONYMOUS";
        CustomerDeliveryAddressResponse address = customerAddressService.saveAddress(customerId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Customer delivery address saved successfully", address));
    }

    @Operation(summary = "Set default delivery address", description = "Marks a specific saved address as default")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('CUSTOMER')")
    @PutMapping(value = "/customer-addresses/{addressPublicId}/default", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<CustomerDeliveryAddressResponse>> setDefaultAddress(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String addressPublicId) {

        String customerId = principal != null ? principal.getUserId() : "ANONYMOUS";
        CustomerDeliveryAddressResponse response = customerAddressService.setDefaultAddress(customerId, addressPublicId);
        return ResponseEntity.ok(ApiResponse.success("Default delivery address updated successfully", response));
    }

    @Operation(summary = "Delete saved delivery address", description = "Removes a customer saved delivery address")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('CUSTOMER')")
    @DeleteMapping(value = "/customer-addresses/{addressPublicId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<Void>> deleteCustomerAddress(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String addressPublicId) {

        String customerId = principal != null ? principal.getUserId() : "ANONYMOUS";
        customerAddressService.deleteAddress(customerId, addressPublicId);
        return ResponseEntity.ok(ApiResponse.success("Customer delivery address deleted successfully", null));
    }

    // =========================================================================
    // Backward Compatibility Endpoints for Legacy Clients
    // =========================================================================

    @Operation(summary = "Get active delivery zones (Legacy)", description = "Maintained for backward compatibility. Returns active zones or store delivery area")
    @GetMapping(value = "/zones", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<List<CustomerDeliveryZoneResponse>>> getActiveZones() {
        List<CustomerDeliveryZoneResponse> zones = legacyDeliveryZoneService.getActiveCustomerZones();
        return ResponseEntity.ok(ApiResponse.success("Active delivery zones retrieved successfully", zones));
    }

    @Operation(summary = "Resolve delivery zone by coordinates (Legacy)", description = "Maintained for backward compatibility. Resolves availability from query coordinates")
    @GetMapping(value = "/zones/resolve", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<DeliveryAvailabilityResponse>> resolveZone(
            @Parameter(description = "Latitude of delivery location", example = "15.8281") @RequestParam Double latitude,
            @Parameter(description = "Longitude of delivery location", example = "78.0373") @RequestParam Double longitude) {

        CheckDeliveryAvailabilityRequest request = CheckDeliveryAvailabilityRequest.builder()
                .latitude(latitude)
                .longitude(longitude)
                .build();
        DeliveryAvailabilityResponse response = deliveryEligibilityService.checkAvailability(request);
        return ResponseEntity.ok(ApiResponse.success(response.getMessage(), response));
    }
}
