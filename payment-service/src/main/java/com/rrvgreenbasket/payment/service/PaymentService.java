package com.rrvgreenbasket.payment.service;

import com.rrvgreenbasket.payment.dto.request.PaymentFilterRequest;
import com.rrvgreenbasket.payment.dto.response.*;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface PaymentService {

    PaymentResponse getPaymentDetails(String paymentPublicId, String customerPublicId, boolean isAdmin);

    PaymentStatusResponse getPaymentStatus(String paymentPublicId, String customerPublicId, boolean isAdmin);

    PageResponse<PaymentHistoryResponse> getCustomerPaymentHistory(String customerPublicId, Pageable pageable);

    OnlinePaymentInitiateResponse retryPayment(String paymentPublicId, String customerPublicId);

    List<PaymentRefundResponse> getPaymentRefunds(String paymentPublicId, String customerPublicId, boolean isAdmin);

    PageResponse<PaymentHistoryResponse> getAdminPayments(PaymentFilterRequest filter, Pageable pageable);

    PaymentStatisticsResponse getPaymentStatistics();

    List<PaymentStatusHistoryResponse> getPaymentStatusHistory(String paymentPublicId);

    List<PaymentTransactionResponse> getPaymentTransactions(String paymentPublicId);

    PageResponse<CodCollectionResponse> getAllCodCollections(Pageable pageable);
}
