package com.rrvgreenbasket.customer.mapper;

import com.rrvgreenbasket.customer.dto.request.AddressRequest;
import com.rrvgreenbasket.customer.dto.response.AddressResponse;
import com.rrvgreenbasket.customer.entity.Customer;
import com.rrvgreenbasket.customer.entity.CustomerAddress;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * AddressMapper
 *
 * <p><strong>What it does:</strong></p>
 * Converts between {@link CustomerAddress} entities and address DTOs.
 */
@Component
public class AddressMapper {

    public AddressResponse toResponse(CustomerAddress address) {
        if (address == null) {
            return null;
        }

        return AddressResponse.builder()
                .id(address.getPublicId())
                .customerId(address.getCustomer() != null ? address.getCustomer().getPublicId() : null)
                .addressType(address.getAddressType())
                .receiverName(address.getReceiverName())
                .receiverMobile(address.getReceiverMobile())
                .addressLine1(address.getAddressLine1())
                .addressLine2(address.getAddressLine2())
                .landmark(address.getLandmark())
                .city(address.getCity())
                .state(address.getState())
                .pincode(address.getPincode())
                .country(address.getCountry())
                .isDefault(address.isDefault())
                .createdAt(address.getCreatedAt())
                .updatedAt(address.getUpdatedAt())
                .build();
    }

    public CustomerAddress toEntity(AddressRequest request, Customer customer) {
        if (request == null) {
            return null;
        }

        return CustomerAddress.builder()
                .publicId(UUID.randomUUID().toString())
                .customer(customer)
                .addressType(request.getAddressType())
                .receiverName(request.getReceiverName().trim())
                .receiverMobile(request.getReceiverMobile().trim())
                .addressLine1(request.getAddressLine1().trim())
                .addressLine2(request.getAddressLine2() != null ? request.getAddressLine2().trim() : null)
                .landmark(request.getLandmark() != null ? request.getLandmark().trim() : null)
                .city(request.getCity().trim())
                .state(request.getState().trim())
                .pincode(request.getPincode().trim())
                .country(request.getCountry() != null && !request.getCountry().isBlank() ? request.getCountry().trim() : "India")
                .isDefault(Boolean.TRUE.equals(request.getIsDefault()))
                .build();
    }

    public void updateEntity(CustomerAddress entity, AddressRequest request) {
        if (request == null || entity == null) {
            return;
        }

        entity.setReceiverName(request.getReceiverName().trim());
        entity.setReceiverMobile(request.getReceiverMobile().trim());
        entity.setAddressLine1(request.getAddressLine1().trim());
        entity.setAddressLine2(request.getAddressLine2() != null ? request.getAddressLine2().trim() : null);
        entity.setLandmark(request.getLandmark() != null ? request.getLandmark().trim() : null);
        entity.setCity(request.getCity().trim());
        entity.setState(request.getState().trim());
        entity.setPincode(request.getPincode().trim());
        if (request.getCountry() != null && !request.getCountry().isBlank()) {
            entity.setCountry(request.getCountry().trim());
        }
        if (request.getAddressType() != null) {
            entity.setAddressType(request.getAddressType());
        }
        if (request.getIsDefault() != null) {
            entity.setDefault(request.getIsDefault());
        }
    }
}
