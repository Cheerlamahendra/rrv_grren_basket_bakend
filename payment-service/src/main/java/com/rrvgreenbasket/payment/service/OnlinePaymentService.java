package com.rrvgreenbasket.payment.service;

import com.rrvgreenbasket.payment.dto.request.OnlinePaymentInitiateRequest;
import com.rrvgreenbasket.payment.dto.request.PaymentVerificationRequest;
import com.rrvgreenbasket.payment.dto.response.OnlinePaymentInitiateResponse;
import com.rrvgreenbasket.payment.dto.response.PaymentResponse;

public interface OnlinePaymentService {

    OnlinePaymentInitiateResponse initiatePayment(OnlinePaymentInitiateRequest request, String customerPublicId);

    PaymentResponse verifyPayment(PaymentVerificationRequest request, String customerPublicId);
}
