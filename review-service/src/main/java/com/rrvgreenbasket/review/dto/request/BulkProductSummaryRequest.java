package com.rrvgreenbasket.review.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkProductSummaryRequest {

    @NotEmpty(message = "Product public IDs list cannot be empty")
    @Size(max = 50, message = "Maximum 50 product IDs allowed per request")
    private List<String> productPublicIds;
}
