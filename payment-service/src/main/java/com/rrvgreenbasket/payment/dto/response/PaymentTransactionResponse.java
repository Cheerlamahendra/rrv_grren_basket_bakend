package com.rrvgreenbasket.payment.dto.response;

import com.rrvgreenbasket.payment.enums.PaymentProvider;
import com.rrvgreenbasket.payment.enums.TransactionStatus;
import com.rrvgreenbasket.payment.enums.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentTransactionResponse {

    private String transactionPublicId;
    private TransactionType transactionType;
    private TransactionStatus transactionStatus;
    private BigDecimal amount;
    private String currency;
    private PaymentProvider provider;
    private String providerOrderId;
    private String providerPaymentId;
    private String failureCode;
    private String failureDescription;
    private Instant createdAt;
}
