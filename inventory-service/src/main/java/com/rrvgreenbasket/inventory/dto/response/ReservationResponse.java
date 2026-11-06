package com.rrvgreenbasket.inventory.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rrvgreenbasket.inventory.entity.StockReservationRequest;
import com.rrvgreenbasket.inventory.enums.ReservationStatus;
import lombok.*;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReservationResponse {

    private String publicId;
    private String orderPublicId;
    private String idempotencyKey;
    private ReservationStatus status;
    private Instant expiresAt;
    private Instant createdAt;
    private List<ReservationItemResponse> items;

    public static ReservationResponse fromEntity(StockReservationRequest request) {
        if (request == null) {
            return null;
        }
        List<ReservationItemResponse> itemResponses = request.getItems() != null
                ? request.getItems().stream().map(ReservationItemResponse::fromEntity).collect(Collectors.toList())
                : Collections.emptyList();

        return ReservationResponse.builder()
                .publicId(request.getPublicId())
                .orderPublicId(request.getOrderPublicId())
                .idempotencyKey(request.getIdempotencyKey())
                .status(request.getStatus())
                .expiresAt(request.getExpiresAt())
                .createdAt(request.getCreatedAt())
                .items(itemResponses)
                .build();
    }
}
