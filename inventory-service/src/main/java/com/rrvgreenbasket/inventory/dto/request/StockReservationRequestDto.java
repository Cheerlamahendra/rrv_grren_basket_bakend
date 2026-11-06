package com.rrvgreenbasket.inventory.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockReservationRequestDto {

    @NotBlank(message = "Order public ID is required")
    @Size(max = 36, message = "Order public ID cannot exceed 36 characters")
    private String orderPublicId;

    @NotBlank(message = "Idempotency key is required")
    @Size(max = 100, message = "Idempotency key cannot exceed 100 characters")
    private String idempotencyKey;

    @Min(value = 1, message = "TTL must be at least 1 minute")
    @Max(value = 1440, message = "TTL cannot exceed 1440 minutes (24 hours)")
    @Builder.Default
    private Integer ttlMinutes = 15;

    @NotEmpty(message = "Reservation must contain at least one item")
    @Valid
    private List<ReservationItemDto> items;
}
