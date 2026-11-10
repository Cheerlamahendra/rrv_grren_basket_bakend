package com.rrvgreenbasket.payment.client;

import com.rrvgreenbasket.payment.client.dto.OrderDetailsDto;

public interface OrderServiceClient {

    OrderDetailsDto getOrderForPayment(String orderPublicId);

    void notifyPaymentStatus(String orderPublicId, String eventType, Object payload);
}
