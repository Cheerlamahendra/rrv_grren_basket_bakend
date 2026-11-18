package com.rrvgreenbasket.order.client.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReservationItemClientDto {

    private String productPublicId;
    private BigDecimal quantity;
    private String unitOfMeasure;
}
