package com.rrvgreenbasket.wishlist.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Bulk wishlist status check response")
public class WishlistBulkStatusResponse {

    @Schema(description = "Customer public UUID", example = "550e8400-e29b-41d4-a716-446655440000")
    private String customerPublicId;

    @Builder.Default
    @Schema(description = "Map of product UUID to wishlisted boolean flag")
    private Map<String, Boolean> statusMap = new HashMap<>();

    @Builder.Default
    @Schema(description = "List of product status items")
    private List<WishlistStatusResponse> items = new ArrayList<>();
}
