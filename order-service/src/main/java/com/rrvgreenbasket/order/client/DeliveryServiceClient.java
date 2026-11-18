package com.rrvgreenbasket.order.client;

import com.rrvgreenbasket.order.client.dto.DeliveryAvailabilityClientResponse;

public interface DeliveryServiceClient {
    DeliveryAvailabilityClientResponse checkAvailability(Double latitude, Double longitude, String address);
}
