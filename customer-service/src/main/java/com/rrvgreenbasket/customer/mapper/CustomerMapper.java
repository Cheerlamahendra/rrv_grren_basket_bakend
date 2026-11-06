package com.rrvgreenbasket.customer.mapper;

import com.rrvgreenbasket.customer.dto.response.AddressResponse;
import com.rrvgreenbasket.customer.dto.response.CustomerResponse;
import com.rrvgreenbasket.customer.entity.Customer;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * CustomerMapper
 *
 * <p><strong>What it does:</strong></p>
 * Converts between {@link Customer} entities and customer DTOs.
 */
@Component
public class CustomerMapper {

    private final AddressMapper addressMapper;

    public CustomerMapper(AddressMapper addressMapper) {
        this.addressMapper = addressMapper;
    }

    public CustomerResponse toResponse(Customer customer) {
        if (customer == null) {
            return null;
        }

        List<AddressResponse> addressResponses = customer.getAddresses() != null
                ? customer.getAddresses().stream()
                        .map(addressMapper::toResponse)
                        .toList()
                : Collections.emptyList();

        return CustomerResponse.builder()
                .id(customer.getPublicId())
                .authUserId(customer.getAuthUserId())
                .fullName(customer.getFullName())
                .email(customer.getEmail())
                .mobileNumber(customer.getMobileNumber())
                .profileImageUrl(customer.getProfileImageUrl())
                .status(customer.getStatus())
                .createdAt(customer.getCreatedAt())
                .updatedAt(customer.getUpdatedAt())
                .addresses(addressResponses)
                .build();
    }
}
