package com.rrvgreenbasket.order.dto.response;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderDetailsDto {

    private String orderPublicId;
    private String customerPublicId;
    private BigDecimal totalAmount;
    private String currency;
    private String orderStatus;
    private boolean paymentEligible;
    private String paymentMethod;
}
