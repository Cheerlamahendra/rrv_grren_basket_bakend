package com.rrvgreenbasket.customer.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * CreateCustomerProfileRequest
 *
 * <p><strong>What it does:</strong></p>
 * Inbound payload for initializing a customer profile.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateCustomerProfileRequest {

    /**
     * Optional in request payload; if omitted, automatically extracted
     * from the authenticated user's context (X-User-Id header or JWT).
     */
    private String authUserId;

    @NotBlank(message = "Full name is required")
    @Size(min = 2, max = 150, message = "Full name must be between 2 and 150 characters")
    private String fullName;

    @NotBlank(message = "Mobile number is required")
    @Pattern(regexp = "^(\\+91)?[6-9]\\d{9}$", message = "Mobile number must be a valid 10-digit Indian number optionally prefixed with +91")
    private String mobileNumber;

    @Email(message = "Email must be a valid email format")
    @Size(max = 254, message = "Email cannot exceed 254 characters")
    private String email;

    @Size(max = 500, message = "Profile image URL cannot exceed 500 characters")
    private String profileImageUrl;
}
