package com.rrvgreenbasket.inventory.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rrvgreenbasket.inventory.entity.StockAlert;
import com.rrvgreenbasket.inventory.enums.AlertStatus;
import com.rrvgreenbasket.inventory.enums.AlertType;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StockAlertResponse {

    private String publicId;
    private String productPublicId;
    private AlertType alertType;
    private BigDecimal currentQuantity;
    private BigDecimal thresholdQuantity;
    private String unitOfMeasure;
    private AlertStatus alertStatus;
    private Instant createdAt;
    private Instant resolvedAt;

    public static StockAlertResponse fromEntity(StockAlert alert) {
        if (alert == null) {
            return null;
        }
        return StockAlertResponse.builder()
                .publicId(alert.getPublicId())
                .productPublicId(alert.getProductPublicId())
                .alertType(alert.getAlertType())
                .currentQuantity(alert.getCurrentQuantity())
                .thresholdQuantity(alert.getThresholdQuantity())
                .unitOfMeasure(alert.getUnitOfMeasure() != null ? alert.getUnitOfMeasure().name() : null)
                .alertStatus(alert.getAlertStatus())
                .createdAt(alert.getCreatedAt())
                .resolvedAt(alert.getResolvedAt())
                .build();
    }
}
