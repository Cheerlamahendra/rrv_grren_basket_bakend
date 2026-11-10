package com.rrvgreenbasket.payment.service;

public interface PaymentReconciliationService {

    void reconcilePendingPayments();

    void reconcilePayment(String paymentPublicId);
}
