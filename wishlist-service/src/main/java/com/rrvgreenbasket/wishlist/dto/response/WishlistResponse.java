package com.rrvgreenbasket.wishlist.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Customer wishlist payload containing all wishlisted items")
public class WishlistResponse {

    @Schema(description = "Unique public UUID of the wishlist", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
    private String wishlistPublicId;

    @Schema(description = "Customer public UUID", example = "550e8400-e29b-41d4-a716-446655440000")
    private String customerPublicId;

    @Schema(description = "Total number of items in the wishlist", example = "5")
    private Integer totalItems;

    @Builder.Default
    @Schema(description = "List of products in the wishlist")
    private List<WishlistProductItemResponse> items = new ArrayList<>();
}
