package com.rrvgreenbasket.order.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class CartClientResponse {

    private String publicId;
    private String customerPublicId;
    private String status;
    private String currency;
    private BigDecimal subtotal;
    private BigDecimal currentSubtotal;
    private Integer itemCount;
    private List<CartItemClientDto> items;
}
