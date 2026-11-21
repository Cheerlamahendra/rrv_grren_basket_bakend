package com.rrvgreenbasket.customer.controller;

import com.rrvgreenbasket.customer.dto.request.AddressRequest;
import com.rrvgreenbasket.customer.dto.response.AddressResponse;
import com.rrvgreenbasket.customer.dto.response.ApiResponse;
import com.rrvgreenbasket.customer.dto.response.CustomerResponse;
import com.rrvgreenbasket.customer.entity.Customer;
import com.rrvgreenbasket.customer.exception.ResourceNotFoundException;
import com.rrvgreenbasket.customer.mapper.CustomerMapper;
import com.rrvgreenbasket.customer.repository.CustomerRepository;
import com.rrvgreenbasket.customer.service.AddressService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * InternalCustomerController
 *
 * <p><strong>What it does:</strong></p>
 * Microservice-internal endpoints for State Verification Probes, Customer Profile Lookups,
 * Address Snapshots, and Compensation Cleanups during sagas.
 * Protected by <code>InternalServiceSecurityFilter</code> with HMAC-SHA256 canonical request signing.
 */
@RestController
@RequestMapping("/api/customers/internal")
@Tag(name = "Internal Customer API", description = "Internal service-to-service endpoints for saga coordination")
public class InternalCustomerController {

    private static final Logger log = LoggerFactory.getLogger(InternalCustomerController.class);

    private final CustomerRepository customerRepository;
    private final CustomerMapper customerMapper;
    private final AddressService addressService;

    public InternalCustomerController(CustomerRepository customerRepository, CustomerMapper customerMapper, AddressService addressService) {
        this.customerRepository = customerRepository;
        this.customerMapper = customerMapper;
        this.addressService = addressService;
    }

    @Operation(summary = "State Verification Probe (Internal S2S)", description = "Verifies whether a profile exists for the given authUserId.")
    @GetMapping("/by-auth-user/{authUserId}")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<CustomerResponse>> getProfileByAuthUserId(@PathVariable("authUserId") String authUserId) {
        log.debug("Internal state verification probe for authUserId='{}'", authUserId);
        Customer customer = customerRepository.findByAuthUserId(authUserId)
                .orElseThrow(() -> new ResourceNotFoundException("No customer profile found for authUserId: " + authUserId));
        return ResponseEntity.ok(ApiResponse.success("Profile verified", customerMapper.toResponse(customer)));
    }

    @Operation(summary = "Get Customer Profile (Internal S2S)", description = "Retrieves authoritative customer profile by publicId or authUserId")
    @GetMapping("/profile/{identifier}")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<CustomerResponse>> getProfileByIdentifier(@PathVariable("identifier") String identifier) {
        log.debug("Internal profile lookup for identifier='{}'", identifier);
        String trimmed = identifier.trim();
        Customer customer = customerRepository.findByPublicId(trimmed)
                .or(() -> customerRepository.findByAuthUserId(trimmed))
                .orElseThrow(() -> new ResourceNotFoundException("Customer profile not found for: " + identifier));
        return ResponseEntity.ok(ApiResponse.success("Customer profile found", customerMapper.toResponse(customer)));
    }

    @Operation(summary = "Get Address (Internal S2S)", description = "Retrieves a customer address by publicId with customer verification")
    @GetMapping("/addresses/{addressPublicId}")
    public ResponseEntity<ApiResponse<AddressResponse>> getAddress(
            @PathVariable("addressPublicId") String addressPublicId,
            @RequestParam("customerId") String customerId) {
        log.debug("Internal address lookup: addressPublicId='{}', customerId='{}'", addressPublicId, customerId);
        AddressResponse address = addressService.getAddressByPublicIdAndCustomer(addressPublicId, customerId);
        return ResponseEntity.ok(ApiResponse.success("Address retrieved", address));
    }

    @Operation(summary = "Save or Retrieve Address (Internal S2S)", description = "Persists a new address or reuses existing identical address during checkout")
    @PostMapping("/addresses")
    public ResponseEntity<ApiResponse<AddressResponse>> saveAddress(
            @RequestParam("customerId") String customerId,
            @Valid @RequestBody AddressRequest request) {
        log.info("Internal address ensure/save for customer='{}', receiver='{}'", customerId, request.getReceiverName());
        AddressResponse address = addressService.addOrGetAddress(customerId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Address saved successfully", address));
    }

    @Operation(summary = "Compensation Cleanup (Internal S2S)", description = "Deletes customer profile if an onboarding saga permanently fails.")
    @DeleteMapping("/by-auth-user/{authUserId}")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> compensateCustomerProfile(@PathVariable("authUserId") String authUserId) {
        log.info("Internal compensation cleanup requested for authUserId='{}'", authUserId);
        customerRepository.findByAuthUserId(authUserId).ifPresent(customer -> {
            customerRepository.delete(customer);
            log.info("Deleted compensated customer profile for authUserId='{}'", authUserId);
        });
        return ResponseEntity.ok(ApiResponse.success("Customer profile compensated successfully", null));
    }
}
