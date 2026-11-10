package com.rrvgreenbasket.store.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateStoreSettingsRequest {

    @NotBlank(message = "Store name is required")
    @Size(max = 150, message = "Store name cannot exceed 150 characters")
    private String storeName;

    @Size(max = 255, message = "Brand tagline cannot exceed 255 characters")
    private String brandTagline;

    @NotBlank(message = "Support email is required")
    @Email(message = "Support email must be a valid email address")
    @Size(max = 150, message = "Support email cannot exceed 150 characters")
    private String supportEmail;

    @NotBlank(message = "Support phone is required")
    @Pattern(regexp = "^[+0-9\\s()-]{7,25}$", message = "Invalid support phone number format")
    private String supportPhone;

    @NotBlank(message = "Physical address is required")
    @Size(max = 500, message = "Physical address cannot exceed 500 characters")
    private String physicalAddress;

    @NotBlank(message = "Website domain URL is required")
    @Pattern(regexp = "^(http|https)://.*$", message = "Website URL must start with http:// or https://")
    @Size(max = 255, message = "Website URL cannot exceed 255 characters")
    private String websiteUrl;
}
