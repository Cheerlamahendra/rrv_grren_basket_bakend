package com.rrvgreenbasket.order.client.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockReservationClientRequest {

    private String orderPublicId;
    private String idempotencyKey;
    @Builder.Default
    private Integer ttlMinutes = 60;
    private List<ReservationItemClientDto> items;
}
