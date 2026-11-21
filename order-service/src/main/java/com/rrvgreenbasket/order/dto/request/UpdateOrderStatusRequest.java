package com.rrvgreenbasket.order.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.rrvgreenbasket.order.enums.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateOrderStatusRequest {

    @NotNull(message = "Order status is required")
    @Schema(example = "CONFIRMED")
    private OrderStatus status;

    @Size(max = 500, message = "Reason cannot exceed 500 characters")
    @Schema(example = "Order verified and sent for packing")
    @JsonAlias({"reason", "remarks"})
    private String reason;

    @Size(max = 500, message = "Remarks cannot exceed 500 characters")
    @Schema(example = "Order packed successfully")
    private String remarks;

    public String getEffectiveReason() {
        if (reason != null && !reason.isBlank()) {
            return reason.trim();
        }
        if (remarks != null && !remarks.isBlank()) {
            return remarks.trim();
        }
        return "Status updated to " + (status != null ? status.name() : "NEW_STATUS");
    }
}
