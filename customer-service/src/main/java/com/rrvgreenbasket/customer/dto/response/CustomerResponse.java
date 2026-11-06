package com.rrvgreenbasket.customer.dto.response;

import com.rrvgreenbasket.customer.enums.AccountStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * CustomerResponse
 *
 * <p><strong>What it does:</strong></p>
 * Outbound DTO representing a customer's profile, contact details, and registered addresses.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerResponse {

    private String id;
    private String authUserId;
    private String fullName;
    private String email;
    private String mobileNumber;
    private String profileImageUrl;
    private AccountStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<AddressResponse> addresses;
}
