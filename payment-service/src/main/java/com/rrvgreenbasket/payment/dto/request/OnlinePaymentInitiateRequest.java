package com.rrvgreenbasket.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OnlinePaymentInitiateRequest {

    @NotBlank(message = "Order public ID is required")
    private String orderPublicId;
}
