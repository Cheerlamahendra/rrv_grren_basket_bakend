package com.rrvgreenbasket.inventory.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rrvgreenbasket.inventory.entity.StockMovement;
import com.rrvgreenbasket.inventory.enums.MovementType;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StockMovementResponse {

    private String publicId;
    private String productPublicId;
    private MovementType movementType;
    private BigDecimal quantity;
    private BigDecimal physicalQuantityBefore;
    private BigDecimal physicalQuantityAfter;
    private BigDecimal availableQuantityBefore;
    private BigDecimal availableQuantityAfter;
    private String unitOfMeasure;
    private String reason;
    private String referenceType;
    private String referenceId;
    private String notes;
    private String performedBy;
    private Instant createdAt;

    public static StockMovementResponse fromEntity(StockMovement movement) {
        if (movement == null) {
            return null;
        }
        return StockMovementResponse.builder()
                .publicId(movement.getPublicId())
                .productPublicId(movement.getProductPublicId())
                .movementType(movement.getMovementType())
                .quantity(movement.getQuantity())
                .physicalQuantityBefore(movement.getPhysicalQuantityBefore())
                .physicalQuantityAfter(movement.getPhysicalQuantityAfter())
                .availableQuantityBefore(movement.getAvailableQuantityBefore())
                .availableQuantityAfter(movement.getAvailableQuantityAfter())
                .unitOfMeasure(movement.getUnitOfMeasure() != null ? movement.getUnitOfMeasure().name() : null)
                .reason(movement.getReason())
                .referenceType(movement.getReferenceType())
                .referenceId(movement.getReferenceId())
                .notes(movement.getNotes())
                .performedBy(movement.getPerformedBy())
                .createdAt(movement.getCreatedAt())
                .build();
    }
}
