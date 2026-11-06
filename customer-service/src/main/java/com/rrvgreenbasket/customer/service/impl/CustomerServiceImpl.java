package com.rrvgreenbasket.customer.service.impl;

import com.rrvgreenbasket.customer.dto.request.CreateCustomerProfileRequest;
import com.rrvgreenbasket.customer.dto.request.UpdateCustomerProfileRequest;
import com.rrvgreenbasket.customer.dto.response.CustomerResponse;
import com.rrvgreenbasket.customer.entity.Customer;
import com.rrvgreenbasket.customer.enums.AccountStatus;
import com.rrvgreenbasket.customer.exception.DuplicateResourceException;
import com.rrvgreenbasket.customer.exception.ForbiddenOperationException;
import com.rrvgreenbasket.customer.exception.ResourceNotFoundException;
import com.rrvgreenbasket.customer.mapper.CustomerMapper;
import com.rrvgreenbasket.customer.repository.CustomerRepository;
import com.rrvgreenbasket.customer.service.CustomerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Optional;
import java.util.UUID;

/**
 * CustomerServiceImpl
 *
 * <p><strong>What it does:</strong></p>
 * Implementation of customer profile creation, retrieval, updates, and status management.
 * Guarantees idempotency for registration sagas and enforces active account status checks.
 */
@Service
public class CustomerServiceImpl implements CustomerService {

    private static final Logger log = LoggerFactory.getLogger(CustomerServiceImpl.class);

    private final CustomerRepository customerRepository;
    private final CustomerMapper customerMapper;

    public CustomerServiceImpl(CustomerRepository customerRepository, CustomerMapper customerMapper) {
        this.customerRepository = customerRepository;
        this.customerMapper = customerMapper;
    }

    @Override
    @Transactional
    public CustomerResponse createCustomerProfile(CreateCustomerProfileRequest request, String currentAuthUserId) {
        String effectiveAuthUserId = StringUtils.hasText(request.getAuthUserId())
                ? request.getAuthUserId().trim()
                : currentAuthUserId;

        if (!StringUtils.hasText(effectiveAuthUserId)) {
            throw new IllegalArgumentException("Authentication identity (authUserId) is required to create a profile.");
        }

        log.info("Creating customer profile for authUserId='{}', mobile='{}'", effectiveAuthUserId, request.getMobileNumber());

        // 1. Idempotency Check: If profile already exists for this Auth User, check mobile match
        Optional<Customer> existingOpt = customerRepository.findByAuthUserId(effectiveAuthUserId);
        if (existingOpt.isPresent()) {
            Customer existing = existingOpt.get();
            String incomingMobile = request.getMobileNumber().trim().replaceAll("^\\+91", "");
            String existingMobile = existing.getMobileNumber().trim().replaceAll("^\\+91", "");
            if (incomingMobile.equals(existingMobile)) {
                log.info("Idempotent profile creation for existing customer with authUserId='{}'", effectiveAuthUserId);
                return customerMapper.toResponse(existing);
            }
            throw new DuplicateResourceException("A customer profile already exists for this authenticated user.");
        }

        // 2. Check mobile uniqueness & Idempotency
        String normalizedMobile = request.getMobileNumber().trim();
        String mobileWithoutPrefix = normalizedMobile.replaceAll("^\\+91", "");
        String mobileWithPrefix = normalizedMobile.startsWith("+91") ? normalizedMobile : "+91" + normalizedMobile;

        Optional<Customer> mobileExistingOpt = customerRepository.findByMobileNumber(mobileWithPrefix)
                .or(() -> customerRepository.findByMobileNumber(mobileWithoutPrefix));
        if (mobileExistingOpt.isPresent()) {
            Customer existing = mobileExistingOpt.get();
            if (effectiveAuthUserId.equals(existing.getAuthUserId())) {
                log.info("Idempotent profile creation for existing customer with mobileNumber='{}'", normalizedMobile);
                return customerMapper.toResponse(existing);
            }
            throw new DuplicateResourceException("Customer with mobile number " + normalizedMobile + " already exists.");
        }

        // 3. Check email uniqueness if provided
        String normalizedEmail = StringUtils.hasText(request.getEmail()) ? request.getEmail().trim().toLowerCase() : null;
        if (normalizedEmail != null && customerRepository.existsByEmail(normalizedEmail)) {
            throw new DuplicateResourceException("Customer with email " + normalizedEmail + " already exists.");
        }

        Customer customer = Customer.builder()
                .publicId(UUID.randomUUID().toString())
                .authUserId(effectiveAuthUserId)
                .fullName(request.getFullName().trim())
                .mobileNumber(normalizedMobile)
                .email(normalizedEmail)
                .profileImageUrl(request.getProfileImageUrl() != null ? request.getProfileImageUrl().trim() : null)
                .status(AccountStatus.ACTIVE)
                .build();

        Customer savedCustomer = customerRepository.save(customer);
        log.info("Customer profile created successfully with publicId='{}'", savedCustomer.getPublicId());
        return customerMapper.toResponse(savedCustomer);
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerResponse getCustomerProfileByAuthUserId(String authUserId) {
        log.debug("Retrieving customer profile by authUserId='{}'", authUserId);
        Customer customer = customerRepository.findByAuthUserId(authUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer profile not found for user: " + authUserId));

        if (customer.getStatus() != AccountStatus.ACTIVE) {
            throw new ForbiddenOperationException("Customer account is inactive or suspended.");
        }

        return customerMapper.toResponse(customer);
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerResponse getCustomerProfileByPublicId(String publicId, String currentAuthUserId, boolean isAdmin) {
        log.debug("Retrieving customer profile by publicId='{}'", publicId);
        Customer customer = customerRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer profile not found with ID: " + publicId));

        if (!isAdmin && !customer.getAuthUserId().equals(currentAuthUserId)) {
            log.warn("Access denied: User '{}' attempted to view profile of '{}'", currentAuthUserId, publicId);
            throw new ForbiddenOperationException("You do not have permission to view this customer profile.");
        }

        if (!isAdmin && customer.getStatus() != AccountStatus.ACTIVE) {
            throw new ForbiddenOperationException("Customer account is inactive or suspended.");
        }

        return customerMapper.toResponse(customer);
    }

    @Override
    @Transactional
    public CustomerResponse updateCustomerProfile(String authUserId, UpdateCustomerProfileRequest request) {
        log.info("Updating customer profile for authUserId='{}'", authUserId);
        Customer customer = customerRepository.findByAuthUserId(authUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer profile not found for user: " + authUserId));

        if (customer.getStatus() != AccountStatus.ACTIVE) {
            throw new ForbiddenOperationException("Customer account is inactive or suspended.");
        }

        if (StringUtils.hasText(request.getFullName())) {
            customer.setFullName(request.getFullName().trim());
        }

        if (request.getEmail() != null) {
            String newEmail = request.getEmail().trim().toLowerCase();
            if (!newEmail.equalsIgnoreCase(customer.getEmail())) {
                if (customerRepository.existsByEmail(newEmail)) {
                    throw new DuplicateResourceException("Email address " + newEmail + " is already in use by another customer.");
                }
                customer.setEmail(newEmail);
            }
        }

        if (request.getProfileImageUrl() != null) {
            customer.setProfileImageUrl(request.getProfileImageUrl().trim());
        }

        Customer updatedCustomer = customerRepository.save(customer);
        log.info("Customer profile updated for publicId='{}'", updatedCustomer.getPublicId());
        return customerMapper.toResponse(updatedCustomer);
    }

    @Override
    @Transactional
    public CustomerResponse updateCustomerProfileByAdmin(String publicId, UpdateCustomerProfileRequest request) {
        log.info("Admin updating customer profile for publicId='{}'", publicId);
        Customer customer = customerRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer profile not found with ID: " + publicId));

        if (StringUtils.hasText(request.getFullName())) {
            customer.setFullName(request.getFullName().trim());
        }

        if (request.getEmail() != null) {
            String newEmail = request.getEmail().trim().toLowerCase();
            if (!newEmail.equalsIgnoreCase(customer.getEmail())) {
                if (customerRepository.existsByEmail(newEmail)) {
                    throw new DuplicateResourceException("Email address " + newEmail + " is already in use by another customer.");
                }
                customer.setEmail(newEmail);
            }
        }

        if (request.getProfileImageUrl() != null) {
            customer.setProfileImageUrl(request.getProfileImageUrl().trim());
        }

        Customer updatedCustomer = customerRepository.save(customer);
        return customerMapper.toResponse(updatedCustomer);
    }

    @Override
    @Transactional
    public CustomerResponse updateCustomerStatus(String publicId, AccountStatus newStatus) {
        log.info("Updating status to '{}' for customer publicId='{}'", newStatus, publicId);
        Customer customer = customerRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer profile not found with ID: " + publicId));

        customer.setStatus(newStatus);
        Customer updatedCustomer = customerRepository.save(customer);
        return customerMapper.toResponse(updatedCustomer);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CustomerResponse> searchCustomers(String query, AccountStatus status, Pageable pageable) {
        log.debug("Searching customers query='{}', status='{}', pageable={}", query, status, pageable);
        return customerRepository.searchCustomers(query, status, pageable)
                .map(customerMapper::toResponse);
    }
}
