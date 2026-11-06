package com.rrvgreenbasket.product.dto.request;

import com.rrvgreenbasket.product.enums.ProductStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateStatusRequest {

    @NotNull(message = "status is required")
    private ProductStatus status;
}
