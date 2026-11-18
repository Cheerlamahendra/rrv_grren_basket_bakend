package com.rrvgreenbasket.order.client;

import com.rrvgreenbasket.order.client.dto.ReservationClientResponse;
import com.rrvgreenbasket.order.client.dto.StockReservationClientRequest;

public interface InventoryServiceClient {

    ReservationClientResponse createReservation(StockReservationClientRequest request);

    ReservationClientResponse confirmReservation(String reservationIdentifier);

    ReservationClientResponse releaseReservation(String reservationIdentifier);
}
