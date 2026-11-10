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
@Schema(description = "Customer wishlist total item count")
public class WishlistCountResponse {

    @Schema(description = "Total number of wishlisted items", example = "5")
    private Integer totalItems;
}
