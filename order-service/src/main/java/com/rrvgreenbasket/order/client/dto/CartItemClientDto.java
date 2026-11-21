package com.rrvgreenbasket.order.client.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class CartItemClientDto {

    private String publicId;
    private String productPublicId;
    private String productName;
    private String productImageUrl;
    @JsonAlias({"quantityKg", "qty"})
    private BigDecimal quantity;
    private BigDecimal quantityKg;
    private String unitOfMeasure;
    private BigDecimal pricePerKg;
    private BigDecimal currentUnitPrice;
    @JsonAlias({"totalPrice", "itemTotal"})
    private BigDecimal itemSubtotal;
    private Boolean inStock;
    private Boolean sufficient;
}
