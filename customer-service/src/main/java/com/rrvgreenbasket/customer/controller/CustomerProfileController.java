package com.rrvgreenbasket.customer.controller;

import com.rrvgreenbasket.customer.dto.request.CreateCustomerProfileRequest;
import com.rrvgreenbasket.customer.dto.request.UpdateCustomerProfileRequest;
import com.rrvgreenbasket.customer.dto.request.UpdateCustomerStatusRequest;
import com.rrvgreenbasket.customer.dto.response.ApiResponse;
import com.rrvgreenbasket.customer.dto.response.CustomerResponse;
import com.rrvgreenbasket.customer.enums.AccountStatus;
import com.rrvgreenbasket.customer.security.UserPrincipal;
import com.rrvgreenbasket.customer.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * CustomerProfileController
 *
 * <p><strong>What it does:</strong></p>
 * REST controller managing customer profile creation, retrieval, updates,
 * and administrative status management.
 */
@RestController
@RequestMapping("/api/customers")
@Tag(name = "Customer Profiles", description = "Endpoints for managing customer profile information and lifecycle")
@SecurityRequirement(name = "bearerAuth")
public class CustomerProfileController {

    private final CustomerService customerService;

    public CustomerProfileController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @PostMapping
    @Operation(summary = "Create customer profile", description = "Initializes a customer profile bound to the authenticated user")
    public ResponseEntity<ApiResponse<CustomerResponse>> createCustomerProfile(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateCustomerProfileRequest request) {

        String authUserId = principal != null ? principal.getUserId() : request.getAuthUserId();
        CustomerResponse response = customerService.createCustomerProfile(request, authUserId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Customer profile created successfully", response));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Get current customer profile", description = "Retrieves the full profile and addresses of the currently authenticated customer")
    public ResponseEntity<ApiResponse<CustomerResponse>> getCurrentCustomerProfile(
            @AuthenticationPrincipal UserPrincipal principal) {

        CustomerResponse response = customerService.getCustomerProfileByAuthUserId(principal.getUserId());
        return ResponseEntity.ok(ApiResponse.success("Customer profile retrieved successfully", response));
    }

    @PutMapping("/me")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Update current customer profile", description = "Updates full name, email, or profile photo for the authenticated customer")
    public ResponseEntity<ApiResponse<CustomerResponse>> updateCurrentCustomerProfile(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdateCustomerProfileRequest request) {

        CustomerResponse response = customerService.updateCustomerProfile(principal.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.success("Customer profile updated successfully", response));
    }

    @GetMapping("/{customerId}")
    @Operation(summary = "Get customer profile by ID", description = "Retrieves customer profile by public UUID. Accessible by profile owner or admin")
    public ResponseEntity<ApiResponse<CustomerResponse>> getCustomerProfileById(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("customerId") String customerId) {

        boolean isAdmin = principal.isAdmin();
        CustomerResponse response = customerService.getCustomerProfileByPublicId(customerId, principal.getUserId(), isAdmin);
        return ResponseEntity.ok(ApiResponse.success("Customer profile retrieved successfully", response));
    }

    @PutMapping("/{customerId}")
    @Operation(summary = "Update customer profile by ID", description = "Updates customer profile by public UUID. Accessible by owner or admin")
    public ResponseEntity<ApiResponse<CustomerResponse>> updateCustomerProfileById(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("customerId") String customerId,
            @Valid @RequestBody UpdateCustomerProfileRequest request) {

        CustomerResponse response;
        if (principal.isAdmin()) {
            response = customerService.updateCustomerProfileByAdmin(customerId, request);
        } else {
            // Verify ownership first
            customerService.getCustomerProfileByPublicId(customerId, principal.getUserId(), false);
            response = customerService.updateCustomerProfile(principal.getUserId(), request);
        }
        return ResponseEntity.ok(ApiResponse.success("Customer profile updated successfully", response));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "List / search customers (Admin)", description = "Searches customer profiles with pagination and status filtering")
    public ResponseEntity<ApiResponse<Page<CustomerResponse>>> getAllCustomers(
            @RequestParam(name = "query", required = false) String query,
            @RequestParam(name = "status", required = false) AccountStatus status,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "sortBy", defaultValue = "createdAt") String sortBy,
            @RequestParam(name = "sortDirection", defaultValue = "DESC") String sortDirection) {

        Sort sort = sortDirection.equalsIgnoreCase("ASC") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<CustomerResponse> results = customerService.searchCustomers(query, status, pageable);
        return ResponseEntity.ok(ApiResponse.success("Customers retrieved successfully", results));
    }

    @PatchMapping("/{customerId}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Update customer status (Admin)", description = "Mutates customer standing between ACTIVE, INACTIVE, and SUSPENDED")
    public ResponseEntity<ApiResponse<CustomerResponse>> updateCustomerStatus(
            @PathVariable("customerId") String customerId,
            @Valid @RequestBody UpdateCustomerStatusRequest request) {

        CustomerResponse response = customerService.updateCustomerStatus(customerId, request.getStatus());
        return ResponseEntity.ok(ApiResponse.success("Customer status updated successfully", response));
    }
}
