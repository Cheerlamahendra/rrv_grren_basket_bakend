package com.rrvgreenbasket.wishlist.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload to add a product to the customer's wishlist")
public class AddToWishlistRequest {

    @NotBlank(message = "Product public ID is required")
    @Pattern(regexp = "^[0-9a-fA-F-]{36}$", message = "Invalid product UUID format")
    @Schema(description = "Product UUID (publicId) to add", example = "550e8400-e29b-41d4-a716-446655440001")
    private String productPublicId;
}
