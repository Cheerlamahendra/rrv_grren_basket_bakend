package com.rrvgreenbasket.payment.service;

import com.rrvgreenbasket.payment.dto.request.AdminRefundRequest;
import com.rrvgreenbasket.payment.dto.response.PaymentRefundResponse;

public interface RefundService {

    PaymentRefundResponse processRefund(String paymentPublicId, AdminRefundRequest request, String adminReference);
}
