package com.rrvgreenbasket.order.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class CartValidationClientResponse {

    private Boolean isValid;
    private Integer totalItems;
    private Integer validItems;
    private Integer itemsWithPriceChanges;
    private Integer outOfStockItems;
    private Integer insufficientStockItems;
    private Integer unavailableItems;
    private List<CartItemValidationClientDto> items;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CartItemValidationClientDto {
        private String productPublicId;
        private String productName;
        private String status;
        private String message;
    }
}
