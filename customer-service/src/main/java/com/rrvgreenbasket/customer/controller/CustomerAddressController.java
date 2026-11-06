package com.rrvgreenbasket.customer.controller;

import com.rrvgreenbasket.customer.dto.request.AddressRequest;
import com.rrvgreenbasket.customer.dto.response.AddressResponse;
import com.rrvgreenbasket.customer.dto.response.ApiResponse;
import com.rrvgreenbasket.customer.security.UserPrincipal;
import com.rrvgreenbasket.customer.service.AddressService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * CustomerAddressController
 *
 * <p><strong>What it does:</strong></p>
 * REST controller for customer delivery address operations.
 * Supports both nested routes (<code>/api/customers/me/addresses</code>) and
 * direct resource routes (<code>/api/customers/addresses/{addressId}</code>)
 * for maximum flexibility across frontend and microservices consumers.
 */
@RestController
@RequestMapping("/api/customers")
@PreAuthorize("hasRole('CUSTOMER')")
@Tag(name = "Customer Addresses", description = "Endpoints for managing multiple customer delivery addresses and default destination selection")
@SecurityRequirement(name = "bearerAuth")
public class CustomerAddressController {

    private final AddressService addressService;

    public CustomerAddressController(AddressService addressService) {
        this.addressService = addressService;
    }

    @GetMapping("/addresses")
    @Operation(summary = "Get all customer addresses", description = "Returns all delivery addresses belonging to the authenticated customer")
    public ResponseEntity<ApiResponse<List<AddressResponse>>> getMyAddresses(
            @AuthenticationPrincipal UserPrincipal principal) {

        List<AddressResponse> addresses = addressService.getCustomerAddresses(principal.getUserId());
        return ResponseEntity.ok(ApiResponse.success("Addresses retrieved successfully", addresses));
    }

    @PostMapping("/add/addresses")
    @Operation(summary = "Add new delivery address", description = "Adds a delivery address. First address automatically becomes default")
    public ResponseEntity<ApiResponse<AddressResponse>> addAddress(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody AddressRequest request) {

        AddressResponse address = addressService.addAddress(principal.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Address added successfully", address));
    }

    @GetMapping({"/me/addresses/{addressId}", "/addresses/{addressId}"})
    @Operation(summary = "Get address by ID", description = "Retrieves a specific delivery address by public UUID")
    public ResponseEntity<ApiResponse<AddressResponse>> getAddressById(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("addressId") String addressId) {

        AddressResponse address = addressService.getAddressById(principal.getUserId(), addressId);
        return ResponseEntity.ok(ApiResponse.success("Address retrieved successfully", address));
    }

    @PutMapping({"/me/addresses/{addressId}", "/addresses/{addressId}"})
    @Operation(summary = "Update address", description = "Updates delivery address details and default status")
    public ResponseEntity<ApiResponse<AddressResponse>> updateAddress(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("addressId") String addressId,
            @Valid @RequestBody AddressRequest request) {

        AddressResponse address = addressService.updateAddress(principal.getUserId(), addressId, request);
        return ResponseEntity.ok(ApiResponse.success("Address updated successfully", address));
    }

    @DeleteMapping({"/me/addresses/{addressId}", "/addresses/{addressId}"})
    @Operation(summary = "Delete address", description = "Deletes a delivery address. If default was deleted, promotes next newest address")
    public ResponseEntity<ApiResponse<Void>> deleteAddress(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("addressId") String addressId) {

        addressService.deleteAddress(principal.getUserId(), addressId);
        return ResponseEntity.ok(ApiResponse.success("Address deleted successfully", null));
    }

    @PatchMapping({"/me/addresses/{addressId}/default", "/addresses/{addressId}/default"})
    @Operation(summary = "Set address as default", description = "Designates this address as the default delivery destination")
    public ResponseEntity<ApiResponse<AddressResponse>> setDefaultAddress(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("addressId") String addressId) {

        AddressResponse address = addressService.setDefaultAddress(principal.getUserId(), addressId);
        return ResponseEntity.ok(ApiResponse.success("Default address updated successfully", address));
    }
}
