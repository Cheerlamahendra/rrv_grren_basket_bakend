package com.rrvgreenbasket.inventory.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rrvgreenbasket.inventory.enums.StockStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OutboxEventPayload {

    private String eventId;
    private String productPublicId;
    private String eventType;
    private String unitOfMeasure;
    private BigDecimal physicalQuantity;
    private BigDecimal reservedQuantity;
    private BigDecimal availableQuantity;
    private StockStatus stockStatus;
    private Instant timestamp;
}
