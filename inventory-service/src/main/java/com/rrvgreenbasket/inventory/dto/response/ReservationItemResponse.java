package com.rrvgreenbasket.inventory.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rrvgreenbasket.inventory.entity.StockReservationItem;
import com.rrvgreenbasket.inventory.enums.ReservationStatus;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReservationItemResponse {

    private String publicId;
    private String productPublicId;
    private BigDecimal reservedQuantity;
    private String unitOfMeasure;
    private ReservationStatus itemStatus;

    public static ReservationItemResponse fromEntity(StockReservationItem item) {
        if (item == null) {
            return null;
        }
        return ReservationItemResponse.builder()
                .publicId(item.getPublicId())
                .productPublicId(item.getProductPublicId())
                .reservedQuantity(item.getReservedQuantity())
                .unitOfMeasure(item.getUnitOfMeasure() != null ? item.getUnitOfMeasure().name() : null)
                .itemStatus(item.getItemStatus())
                .build();
    }
}
