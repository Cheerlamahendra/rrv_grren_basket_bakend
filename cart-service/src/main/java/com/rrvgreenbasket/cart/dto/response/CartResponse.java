package com.rrvgreenbasket.cart.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rrvgreenbasket.cart.entity.CartStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CartResponse {

    private String cartPublicId;
    private String publicId; // Backward compatibility alias for cartPublicId

    private CartStatus status;

    @Builder.Default
    private List<CartItemResponse> items = new ArrayList<>();

    private Integer totalItems;
    private Integer itemCount; // Backward compatibility alias for totalItems

    private BigDecimal subtotal;
    private BigDecimal totalAmount; // Backward compatibility alias for subtotal
    private BigDecimal previousSubtotal;
    private BigDecimal currentSubtotal;

    @Builder.Default
    private String currency = "INR";

    private boolean priceChanged;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
