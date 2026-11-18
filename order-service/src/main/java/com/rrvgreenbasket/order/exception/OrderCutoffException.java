package com.rrvgreenbasket.order.exception;

import lombok.Getter;

import java.time.LocalTime;

@Getter
public class OrderCutoffException extends RuntimeException {

    private final boolean orderAcceptanceEnabled;
    private final LocalTime openingTime;
    private final LocalTime closingTime;
    private final LocalTime nextOpeningTime;

    public OrderCutoffException(String message, boolean orderAcceptanceEnabled, LocalTime openingTime, LocalTime closingTime, LocalTime nextOpeningTime) {
        super(message);
        this.orderAcceptanceEnabled = orderAcceptanceEnabled;
        this.openingTime = openingTime;
        this.closingTime = closingTime;
        this.nextOpeningTime = nextOpeningTime;
    }
}
