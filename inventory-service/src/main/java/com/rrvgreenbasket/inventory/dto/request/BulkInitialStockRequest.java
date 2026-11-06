package com.rrvgreenbasket.inventory.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@Builder
public class BulkInitialStockRequest {

    @NotEmpty(message = "Bulk initialization must contain at least one item")
    @Valid
    private List<InitialStockRequest> items;

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public BulkInitialStockRequest(List<InitialStockRequest> items) {
        this.items = items;
    }
}
