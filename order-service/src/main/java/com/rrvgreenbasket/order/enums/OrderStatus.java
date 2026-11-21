package com.rrvgreenbasket.order.enums;

import java.util.Set;

public enum OrderStatus {
    PLACED,
    CONFIRMED,
    PROCESSING,
    PACKED,
    OUT_FOR_DELIVERY,
    DELIVERED,
    CANCELLED,
    FAILED;

    public boolean canTransitionTo(OrderStatus next) {
        if (this == next) return true;

        return switch (this) {
            case PLACED -> Set.of(CONFIRMED, CANCELLED, FAILED).contains(next);
            case CONFIRMED -> Set.of(PROCESSING, CANCELLED).contains(next);
            case PROCESSING -> Set.of(PACKED, CANCELLED).contains(next);
            case PACKED -> Set.of(OUT_FOR_DELIVERY, CANCELLED).contains(next);
            case OUT_FOR_DELIVERY -> Set.of(DELIVERED, FAILED).contains(next);
            case DELIVERED, CANCELLED, FAILED -> false;
        };
    }

    public boolean isCancellableByCustomer() {
        return this == PLACED || this == CONFIRMED;
    }

    public boolean isCancellableByAdmin() {
        return this == PLACED || this == CONFIRMED || this == PROCESSING || this == PACKED;
    }

    @com.fasterxml.jackson.annotation.JsonCreator
    public static OrderStatus fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String clean = value.trim().toUpperCase().replace(" ", "_");
        if ("PENDING".equals(clean)) {
            return CONFIRMED;
        }
        for (OrderStatus status : values()) {
            if (status.name().equalsIgnoreCase(clean)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown order status: " + value);
    }
}
