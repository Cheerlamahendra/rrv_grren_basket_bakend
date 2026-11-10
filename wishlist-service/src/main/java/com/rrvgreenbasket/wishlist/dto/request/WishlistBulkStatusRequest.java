package com.rrvgreenbasket.wishlist.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Bulk inquiry payload to check wishlisted status for multiple products")
public class WishlistBulkStatusRequest {

    @NotEmpty(message = "Product public IDs list cannot be empty")
    @Size(max = 50, message = "Maximum 50 products per request")
    @Schema(description = "List of product UUIDs to check", example = "[\"550e8400-e29b-41d4-a716-446655440001\", \"660e8400-e29b-41d4-a716-446655440002\"]")
    private List<@Pattern(regexp = "^[0-9a-fA-F-]{36}$", message = "Invalid product UUID format") String> productPublicIds;
}
