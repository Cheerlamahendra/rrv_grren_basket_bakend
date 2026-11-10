package com.rrvgreenbasket.wishlist.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Detailed product item in customer's wishlist with real-time product information")
public class WishlistProductItemResponse {

    @Schema(description = "Product UUID", example = "550e8400-e29b-41d4-a716-446655440001")
    private String productPublicId;

    @Schema(description = "Product name", example = "Fresh Organic Tomatoes")
    private String productName;

    @Schema(description = "Product slug", example = "fresh-organic-tomatoes")
    private String productSlug;

    @Schema(description = "Product primary image URL", example = "https://cdn.rrvgreenbasket.com/products/tomato.png")
    private String productImage;

    @Schema(description = "Current product price per unit", example = "40.00")
    private BigDecimal price;

    @Schema(description = "Product quantity unit (e.g. KG, PIECE)", example = "KG")
    private String unit;

    @Schema(description = "Real-time product availability status", example = "AVAILABLE")
    private String availabilityStatus;

    @Schema(description = "Stock status (e.g. IN_STOCK, LOW_STOCK, OUT_OF_STOCK)", example = "IN_STOCK")
    private String stockStatus;

    @Schema(description = "Boolean indicator if product is active and in-stock", example = "true")
    private Boolean isAvailable;

    @Schema(description = "Timestamp when product was added to wishlist")
    private Instant addedAt;
}
