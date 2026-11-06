package com.rrvgreenbasket.customer.service;

import com.rrvgreenbasket.customer.dto.request.AddressRequest;
import com.rrvgreenbasket.customer.dto.response.AddressResponse;

import java.util.List;

/**
 * AddressService
 *
 * <p><strong>What it does:</strong></p>
 * Service interface governing customer delivery address management.
 * Guarantees strict customer address ownership and enforces single default address invariants.
 */
public interface AddressService {

    AddressResponse addAddress(String authUserId, AddressRequest request);

    AddressResponse updateAddress(String authUserId, String addressPublicId, AddressRequest request);

    void deleteAddress(String authUserId, String addressPublicId);

    List<AddressResponse> getCustomerAddresses(String authUserId);

    AddressResponse getAddressById(String authUserId, String addressPublicId);

    AddressResponse setDefaultAddress(String authUserId, String addressPublicId);
}
