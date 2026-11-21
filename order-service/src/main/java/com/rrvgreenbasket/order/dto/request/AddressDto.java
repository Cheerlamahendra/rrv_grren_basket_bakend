package com.rrvgreenbasket.order.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AddressDto {

    @Schema(description = "Recipient name for this order", example = "Anand")
    @JsonAlias({"recipientName", "receiverName", "fullName"})
    private String recipientName;

    @Schema(description = "Recipient contact mobile number", example = "8978040537")
    @JsonAlias({"recipientMobile", "receiverMobile", "mobileNumber"})
    private String recipientMobile;

    @Schema(description = "Primary street address line 1", example = "Santhosi Boys Hostel")
    private String addressLine1;

    @Schema(description = "Secondary street address line 2 / locality", example = "Dinnedvarapadu")
    private String addressLine2;

    @Schema(description = "Backward-compatible address line", example = "House No. 10, Example Street")
    private String addressLine;

    @Schema(example = "Near Collector Office")
    private String landmark;

    @Schema(example = "Kurnool")
    private String city;

    @Schema(example = "Andhra Pradesh")
    private String state;

    @Schema(example = "518001")
    private String pincode;

    @Builder.Default
    @Schema(example = "India")
    private String country = "India";

    @NotNull(message = "Delivery latitude is required")
    @DecimalMin(value = "-90.0", message = "Latitude must be >= -90.0")
    @DecimalMax(value = "90.0", message = "Latitude must be <= 90.0")
    @Schema(example = "15.8281")
    private BigDecimal latitude;

    @NotNull(message = "Delivery longitude is required")
    @DecimalMin(value = "-180.0", message = "Longitude must be >= -180.0")
    @DecimalMax(value = "180.0", message = "Longitude must be <= 180.0")
    @Schema(example = "78.0373")
    private BigDecimal longitude;

    @Schema(description = "Public ID of the saved customer address in customer-service", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    @JsonAlias({"addressPublicId", "addressId", "id"})
    private String addressPublicId;

    public String getAddressLine() {
        if (addressLine != null && !addressLine.isBlank()) {
            return addressLine;
        }
        if (addressLine1 != null && !addressLine1.isBlank()) {
            if (addressLine2 != null && !addressLine2.isBlank()) {
                return addressLine1 + ", " + addressLine2;
            }
            return addressLine1;
        }
        return "";
    }

    public String getAddressLine1() {
        if (addressLine1 != null && !addressLine1.isBlank()) {
            return addressLine1;
        }
        return addressLine;
    }
}
