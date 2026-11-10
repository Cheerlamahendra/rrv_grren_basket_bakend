package com.rrvgreenbasket.payment.service;

import com.rrvgreenbasket.payment.dto.request.PartialCodPaymentInitiateRequest;
import com.rrvgreenbasket.payment.dto.response.PartialCodPaymentInitiateResponse;

public interface PartialPaymentService {

    PartialCodPaymentInitiateResponse initiatePartialCod(PartialCodPaymentInitiateRequest request, String customerPublicId);
}
