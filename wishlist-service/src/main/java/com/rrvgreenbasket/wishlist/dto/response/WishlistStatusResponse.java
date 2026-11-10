package com.rrvgreenbasket.wishlist.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Wishlist status check for a single product")
public class WishlistStatusResponse {

    @Schema(description = "Product UUID", example = "550e8400-e29b-41d4-a716-446655440001")
    private String productPublicId;

    @Schema(description = "Whether the product is currently wishlisted by the customer", example = "true")
    private Boolean isWishlisted;
}
