package com.rrvgreenbasket.delivery.service;

import com.rrvgreenbasket.delivery.dto.request.CustomerDeliveryAddressRequest;
import com.rrvgreenbasket.delivery.dto.response.CustomerDeliveryAddressResponse;
import com.rrvgreenbasket.delivery.dto.response.VerifiedAddressResponse;
import com.rrvgreenbasket.delivery.entity.CustomerDeliveryAddress;
import com.rrvgreenbasket.delivery.exception.AddressVerificationException;
import com.rrvgreenbasket.delivery.exception.InvalidRequestException;
import com.rrvgreenbasket.delivery.repository.CustomerDeliveryAddressRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerAddressService {

    private final CustomerDeliveryAddressRepository addressRepository;
    private final AddressVerificationService addressVerificationService;

    @Transactional(readOnly = true)
    public List<CustomerDeliveryAddressResponse> getCustomerAddresses(String customerId) {
        return addressRepository.findByCustomerIdOrderByIsDefaultDescCreatedAtDesc(customerId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional
    public CustomerDeliveryAddressResponse saveAddress(String customerId, CustomerDeliveryAddressRequest request) {
        log.info("Saving customer address for customerId={}, placeId={}", customerId, request.getGooglePlaceId());

        // Verify Place ID with Google Places API to ensure coordinates and address are authentic
        VerifiedAddressResponse verified = addressVerificationService.verifyPlaceId(request.getGooglePlaceId());

        // If marked default, unset any existing default address for this customer
        if (Boolean.TRUE.equals(request.getIsDefault())) {
            addressRepository.findByCustomerIdAndIsDefaultTrue(customerId)
                    .ifPresent(existingDefault -> {
                        existingDefault.setIsDefault(false);
                        addressRepository.save(existingDefault);
                    });
        }

        // Authoritative coordinates and formatted address come from Google Places
        CustomerDeliveryAddress address = CustomerDeliveryAddress.builder()
                .publicId(UUID.randomUUID().toString())
                .customerId(customerId)
                .googlePlaceId(verified.getPlaceId())
                .formattedAddress(verified.getFormattedAddress())
                .addressLine(request.getAddressLine())
                .landmark(request.getLandmark())
                .city(verified.getCity() != null ? verified.getCity() : request.getCity())
                .state(verified.getState() != null ? verified.getState() : request.getState())
                .postalCode(verified.getPostalCode() != null ? verified.getPostalCode() : request.getPostalCode())
                .country(verified.getCountry() != null ? verified.getCountry() : request.getCountry())
                .latitude(verified.getLatitude())
                .longitude(verified.getLongitude())
                .isDefault(Boolean.TRUE.equals(request.getIsDefault()))
                .verified(true)
                .build();

        CustomerDeliveryAddress saved = addressRepository.save(address);
        log.info("Saved customer delivery address: publicId={}", saved.getPublicId());
        return mapToResponse(saved);
    }

    @Transactional
    public CustomerDeliveryAddressResponse setDefaultAddress(String customerId, String addressPublicId) {
        CustomerDeliveryAddress target = addressRepository.findByCustomerIdAndPublicId(customerId, addressPublicId)
                .orElseThrow(() -> new InvalidRequestException("Address not found with public ID: " + addressPublicId));

        addressRepository.findByCustomerIdAndIsDefaultTrue(customerId)
                .ifPresent(existingDefault -> {
                    if (!existingDefault.getPublicId().equals(addressPublicId)) {
                        existingDefault.setIsDefault(false);
                        addressRepository.save(existingDefault);
                    }
                });

        target.setIsDefault(true);
        CustomerDeliveryAddress saved = addressRepository.save(target);
        return mapToResponse(saved);
    }

    @Transactional
    public void deleteAddress(String customerId, String addressPublicId) {
        CustomerDeliveryAddress target = addressRepository.findByCustomerIdAndPublicId(customerId, addressPublicId)
                .orElseThrow(() -> new InvalidRequestException("Address not found with public ID: " + addressPublicId));

        addressRepository.delete(target);
        log.info("Deleted customer delivery address: publicId={}", addressPublicId);
    }

    private CustomerDeliveryAddressResponse mapToResponse(CustomerDeliveryAddress address) {
        return CustomerDeliveryAddressResponse.builder()
                .publicId(address.getPublicId())
                .googlePlaceId(address.getGooglePlaceId())
                .formattedAddress(address.getFormattedAddress())
                .addressLine(address.getAddressLine())
                .landmark(address.getLandmark())
                .city(address.getCity())
                .state(address.getState())
                .postalCode(address.getPostalCode())
                .country(address.getCountry())
                .latitude(address.getLatitude())
                .longitude(address.getLongitude())
                .isDefault(address.getIsDefault())
                .verified(address.getVerified())
                .build();
    }
}
