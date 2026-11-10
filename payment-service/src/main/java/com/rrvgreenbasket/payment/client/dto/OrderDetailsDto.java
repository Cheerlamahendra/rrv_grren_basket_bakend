package com.rrvgreenbasket.payment.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderDetailsDto {

    private String orderPublicId;
    private String customerPublicId;
    private BigDecimal totalAmount;
    private String currency;
    private String orderStatus;
    private boolean paymentEligible;
    private String paymentMethod;
}
