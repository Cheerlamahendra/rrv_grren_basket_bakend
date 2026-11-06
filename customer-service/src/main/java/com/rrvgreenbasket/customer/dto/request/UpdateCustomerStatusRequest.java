package com.rrvgreenbasket.customer.dto.request;

import com.rrvgreenbasket.customer.enums.AccountStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * UpdateCustomerStatusRequest
 *
 * <p><strong>What it does:</strong></p>
 * Payload for updating a customer's account status (ACTIVE, INACTIVE, SUSPENDED).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCustomerStatusRequest {

    @NotNull(message = "Account status is required (ACTIVE, INACTIVE, SUSPENDED)")
    private AccountStatus status;
}
