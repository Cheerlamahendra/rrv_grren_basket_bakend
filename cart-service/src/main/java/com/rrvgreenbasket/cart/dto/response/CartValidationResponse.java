package com.rrvgreenbasket.cart.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CartValidationResponse {

    private String cartPublicId;
    private boolean valid;
    @Builder.Default
    private List<CartItemValidationResponse> items = new ArrayList<>();
    private int totalItems;
    private BigDecimal previousSubtotal;
    private BigDecimal currentSubtotal;
    private String currency;
    private boolean priceChanged;
    private int unavailableItemCount;
    private int insufficientStockCount;

    @Builder.Default
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant validatedAt = Instant.now();

    @Builder.Default
    private List<String> validationMessages = new ArrayList<>();
}
