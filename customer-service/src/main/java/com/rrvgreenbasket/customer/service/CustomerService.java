package com.rrvgreenbasket.customer.service;

import com.rrvgreenbasket.customer.dto.request.CreateCustomerProfileRequest;
import com.rrvgreenbasket.customer.dto.request.UpdateCustomerProfileRequest;
import com.rrvgreenbasket.customer.dto.response.CustomerResponse;
import com.rrvgreenbasket.customer.enums.AccountStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * CustomerService
 *
 * <p><strong>What it does:</strong></p>
 * Business logic contract for managing customer profiles and account status.
 */
public interface CustomerService {

    CustomerResponse createCustomerProfile(CreateCustomerProfileRequest request, String currentAuthUserId);

    CustomerResponse getCustomerProfileByAuthUserId(String authUserId);

    CustomerResponse getCustomerProfileByPublicId(String publicId, String currentAuthUserId, boolean isAdmin);

    CustomerResponse updateCustomerProfile(String authUserId, UpdateCustomerProfileRequest request);

    CustomerResponse updateCustomerProfileByAdmin(String publicId, UpdateCustomerProfileRequest request);

    CustomerResponse updateCustomerStatus(String publicId, AccountStatus newStatus);

    Page<CustomerResponse> searchCustomers(String query, AccountStatus status, Pageable pageable);
}
