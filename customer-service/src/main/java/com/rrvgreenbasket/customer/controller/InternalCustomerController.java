package com.rrvgreenbasket.customer.controller;

import com.rrvgreenbasket.customer.dto.response.ApiResponse;
import com.rrvgreenbasket.customer.dto.response.CustomerResponse;
import com.rrvgreenbasket.customer.entity.Customer;
import com.rrvgreenbasket.customer.exception.ResourceNotFoundException;
import com.rrvgreenbasket.customer.mapper.CustomerMapper;
import com.rrvgreenbasket.customer.repository.CustomerRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * InternalCustomerController
 *
 * <p><strong>What it does:</strong></p>
 * Microservice-internal endpoints for State Verification Probes and Compensation Cleanups
 * during customer registration sagas.
 * Protected by <code>InternalServiceSecurityFilter</code> with HMAC-SHA256 canonical request signing.
 */
@RestController
@RequestMapping("/api/customers/internal")
@Tag(name = "Internal Customer API", description = "Internal service-to-service endpoints for saga coordination")
public class InternalCustomerController {

    private static final Logger log = LoggerFactory.getLogger(InternalCustomerController.class);

    private final CustomerRepository customerRepository;
    private final CustomerMapper customerMapper;

    public InternalCustomerController(CustomerRepository customerRepository, CustomerMapper customerMapper) {
        this.customerRepository = customerRepository;
        this.customerMapper = customerMapper;
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
