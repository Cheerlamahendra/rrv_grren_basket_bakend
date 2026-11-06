package com.rrvgreenbasket.customer.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.rrvgreenbasket.customer.enums.AddressType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AddressRequest
 *
 * <p><strong>What it does:</strong></p>
 * Inbound payload for creating or updating a customer delivery address.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddressRequest {

    @NotBlank(message = "Receiver name is required")
    @Size(min = 2, max = 150, message = "Receiver name must be between 2 and 150 characters")
    private String receiverName;

    @NotBlank(message = "Receiver mobile number is required")
    @Pattern(regexp = "^[6-9]\\d{9}$", message = "Receiver mobile must be a valid 10-digit Indian number starting with 6-9")
    private String receiverMobile;

    @NotBlank(message = "Address line 1 is required")
    @Size(max = 255, message = "Address line 1 cannot exceed 255 characters")
    private String addressLine1;

    @Size(max = 255, message = "Address line 2 cannot exceed 255 characters")
    private String addressLine2;

    @Size(max = 150, message = "Landmark cannot exceed 150 characters")
    private String landmark;

    @NotBlank(message = "City is required")
    @Size(max = 100, message = "City cannot exceed 100 characters")
    private String city;

    @NotBlank(message = "State is required")
    @Size(max = 100, message = "State cannot exceed 100 characters")
    private String state;

    @NotBlank(message = "PIN code is required")
    @Pattern(regexp = "^[1-9][0-9]{5}$", message = "PIN code must be a valid 6-digit Indian postal code")
    private String pincode;

    @Builder.Default
    private String country = "India";

    @NotNull(message = "Address type is required (HOME, WORK, OTHER)")
    @Builder.Default
    private AddressType addressType = AddressType.HOME;

    @JsonProperty("isDefault")
    @Builder.Default
    private Boolean isDefault = false;
}
