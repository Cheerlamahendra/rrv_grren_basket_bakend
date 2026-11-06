package com.rrvgreenbasket.customer.service.impl;

import com.rrvgreenbasket.customer.dto.request.AddressRequest;
import com.rrvgreenbasket.customer.dto.response.AddressResponse;
import com.rrvgreenbasket.customer.entity.Customer;
import com.rrvgreenbasket.customer.entity.CustomerAddress;
import com.rrvgreenbasket.customer.enums.AccountStatus;
import com.rrvgreenbasket.customer.exception.ForbiddenOperationException;
import com.rrvgreenbasket.customer.exception.InvalidRequestException;
import com.rrvgreenbasket.customer.exception.ResourceNotFoundException;
import com.rrvgreenbasket.customer.mapper.AddressMapper;
import com.rrvgreenbasket.customer.repository.CustomerAddressRepository;
import com.rrvgreenbasket.customer.repository.CustomerRepository;
import com.rrvgreenbasket.customer.service.AddressService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * AddressServiceImpl
 *
 * <p><strong>What it does:</strong></p>
 * Implements address lifecycle operations for authenticated customers.
 * Enforces business rules:
 * <ul>
 *   <li>Maximum 10 addresses per customer profile.</li>
 *   <li>The customer's first added address is automatically designated as default.</li>
 *   <li>Only one address per customer is default at any time; setting an address as default atomically resets previous defaults.</li>
 *   <li>Deleting a default address promotes the most recently updated remaining address to default.</li>
 *   <li>All operations are strictly scoped to the authenticated customer's own addresses.</li>
 * </ul>
 */
@Service
public class AddressServiceImpl implements AddressService {

    private static final Logger log = LoggerFactory.getLogger(AddressServiceImpl.class);
    private static final int MAX_ADDRESSES_PER_CUSTOMER = 10;

    private final CustomerRepository customerRepository;
    private final CustomerAddressRepository addressRepository;
    private final AddressMapper addressMapper;

    public AddressServiceImpl(
            CustomerRepository customerRepository,
            CustomerAddressRepository addressRepository,
            AddressMapper addressMapper) {
        this.customerRepository = customerRepository;
        this.addressRepository = addressRepository;
        this.addressMapper = addressMapper;
    }

    private Customer getCustomerByAuthUserId(String authUserId) {
        Customer customer = customerRepository.findByAuthUserId(authUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer profile not found for user: " + authUserId));
        if (customer.getStatus() != AccountStatus.ACTIVE) {
            throw new ForbiddenOperationException("Customer account is inactive or suspended.");
        }
        return customer;
    }

    @Override
    @Transactional
    public AddressResponse addAddress(String authUserId, AddressRequest request) {
        Customer customer = getCustomerByAuthUserId(authUserId);
        log.info("Adding new delivery address for customer publicId='{}'", customer.getPublicId());

        long currentCount = addressRepository.countByCustomerId(customer.getId());
        if (currentCount >= MAX_ADDRESSES_PER_CUSTOMER) {
            throw new InvalidRequestException("Maximum address limit (" + MAX_ADDRESSES_PER_CUSTOMER + ") reached for customer.");
        }

        CustomerAddress address = addressMapper.toEntity(request, customer);

        // Rule: If this is the customer's very first address, automatically make it default
        if (currentCount == 0) {
            address.setDefault(true);
        } else if (address.isDefault()) {
            // Atomic reset of any existing default address for this customer
            addressRepository.resetDefaultAddressesForCustomer(customer.getId());
        }

        CustomerAddress savedAddress = addressRepository.save(address);
        log.info("Address added with publicId='{}', default={}", savedAddress.getPublicId(), savedAddress.isDefault());
        return addressMapper.toResponse(savedAddress);
    }

    @Override
    @Transactional
    public AddressResponse updateAddress(String authUserId, String addressPublicId, AddressRequest request) {
        Customer customer = getCustomerByAuthUserId(authUserId);
        log.info("Updating address publicId='{}' for customer='{}'", addressPublicId, customer.getPublicId());

        CustomerAddress address = addressRepository.findByPublicIdAndCustomerId(addressPublicId, customer.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Address not found with ID: " + addressPublicId));

        boolean wasDefault = address.isDefault();
        boolean willBeDefault = Boolean.TRUE.equals(request.getIsDefault());

        if (!wasDefault && willBeDefault) {
            addressRepository.resetDefaultAddressesForCustomer(customer.getId());
        }

        addressMapper.updateEntity(address, request);

        // If it was already default and user tried to un-default it without specifying another default,
        // preserve default status if it is the only address
        if (wasDefault && !willBeDefault) {
            long totalAddresses = addressRepository.countByCustomerId(customer.getId());
            if (totalAddresses <= 1) {
                address.setDefault(true);
            }
        }

        CustomerAddress updatedAddress = addressRepository.save(address);
        return addressMapper.toResponse(updatedAddress);
    }

    @Override
    @Transactional
    public void deleteAddress(String authUserId, String addressPublicId) {
        Customer customer = getCustomerByAuthUserId(authUserId);
        log.info("Deleting address publicId='{}' for customer='{}'", addressPublicId, customer.getPublicId());

        CustomerAddress address = addressRepository.findByPublicIdAndCustomerId(addressPublicId, customer.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Address not found with ID: " + addressPublicId));

        boolean wasDefault = address.isDefault();
        addressRepository.delete(address);
        addressRepository.flush();

        // If the deleted address was default, promote the newest remaining address to default
        if (wasDefault) {
            addressRepository.findFirstByCustomerIdOrderByUpdatedAtDesc(customer.getId())
                    .ifPresent(remaining -> {
                        remaining.setDefault(true);
                        addressRepository.save(remaining);
                        log.info("Promoted remaining address publicId='{}' to default", remaining.getPublicId());
                    });
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<AddressResponse> getCustomerAddresses(String authUserId) {
        Customer customer = getCustomerByAuthUserId(authUserId);
        log.debug("Listing addresses for customer='{}'", customer.getPublicId());
        return addressRepository.findByCustomerIdOrderByIsDefaultDescCreatedAtDesc(customer.getId())
                .stream()
                .map(addressMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AddressResponse getAddressById(String authUserId, String addressPublicId) {
        Customer customer = getCustomerByAuthUserId(authUserId);
        log.debug("Fetching address publicId='{}' for customer='{}'", addressPublicId, customer.getPublicId());
        CustomerAddress address = addressRepository.findByPublicIdAndCustomerId(addressPublicId, customer.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Address not found with ID: " + addressPublicId));
        return addressMapper.toResponse(address);
    }

    @Override
    @Transactional
    public AddressResponse setDefaultAddress(String authUserId, String addressPublicId) {
        Customer customer = getCustomerByAuthUserId(authUserId);
        log.info("Setting address publicId='{}' as default for customer='{}'", addressPublicId, customer.getPublicId());

        CustomerAddress address = addressRepository.findByPublicIdAndCustomerId(addressPublicId, customer.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Address not found with ID: " + addressPublicId));

        addressRepository.resetDefaultAddressesForCustomer(customer.getId());
        address.setDefault(true);
        CustomerAddress updated = addressRepository.save(address);

        return addressMapper.toResponse(updated);
    }
}
