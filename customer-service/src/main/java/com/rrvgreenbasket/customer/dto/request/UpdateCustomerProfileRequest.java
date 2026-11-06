package com.rrvgreenbasket.customer.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * UpdateCustomerProfileRequest
 *
 * <p><strong>What it does:</strong></p>
 * Inbound payload for mutating customer profile attributes.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCustomerProfileRequest {

    @Size(min = 2, max = 150, message = "Full name must be between 2 and 150 characters")
    private String fullName;

    @Email(message = "Email must be a valid email format")
    @Size(max = 254, message = "Email cannot exceed 254 characters")
    private String email;

    @Size(max = 500, message = "Profile image URL cannot exceed 500 characters")
    private String profileImageUrl;
}
