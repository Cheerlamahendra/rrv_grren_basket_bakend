package com.rrvgreenbasket.cart.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CartItemResponse {

    private String itemPublicId;
    private String publicId; // Backward compatibility alias for itemPublicId

    private String productPublicId;
    private String productName;

    private String productImage;
    private String productImageUrl; // Backward compatibility alias for productImage

    private BigDecimal quantity;
    private BigDecimal quantityKg; // Backward compatibility alias for quantity

    private String unitOfMeasure;

    private BigDecimal previousUnitPrice;
    private BigDecimal pricePerKg; // Backward compatibility alias for previousUnitPrice

    private BigDecimal currentUnitPrice;
    private boolean priceChanged;

    private BigDecimal itemSubtotal;
    private BigDecimal totalPrice; // Backward compatibility alias for itemSubtotal

    private BigDecimal availableQuantity;
    private Boolean inStock;
    private Boolean sufficient;
}
