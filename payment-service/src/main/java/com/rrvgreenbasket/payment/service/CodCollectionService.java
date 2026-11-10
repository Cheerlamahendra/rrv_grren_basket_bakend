package com.rrvgreenbasket.payment.service;

import com.rrvgreenbasket.payment.dto.request.CodCollectionRequest;
import com.rrvgreenbasket.payment.dto.response.CodCollectionResponse;

public interface CodCollectionService {

    CodCollectionResponse collectCod(String paymentPublicId, CodCollectionRequest request, String callerReference);
}
