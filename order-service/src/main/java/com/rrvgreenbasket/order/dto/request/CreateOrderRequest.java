package com.rrvgreenbasket.order.dto.request;

import com.rrvgreenbasket.order.enums.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateOrderRequest {

    @Valid
    @NotNull(message = "Delivery address is required")
    private AddressDto deliveryAddress;

    @Schema(description = "Optional saved customer address public UUID")
    private String addressId;

    @NotNull(message = "Payment method is required (e.g. COD, ONLINE_FULL)")
    @Builder.Default
    @Schema(example = "COD")
    private PaymentMethod paymentMethod = PaymentMethod.COD;

    @Schema(example = "Leave at front doorstep")
    private String notes;

    @Schema(description = "Client-supplied idempotency key to prevent double submissions")
    private String idempotencyKey;
}
