package com.payment.event;

import com.payment.exception.ErrorCode;
import com.payment.model.PaymentStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentCompletedEvent(
    UUID paymentId,
    UUID senderAccountId,
    UUID receiverAccountId,
    BigDecimal amount,
    String currency,
    PaymentStatus status,
    ErrorCode errorCode,
    String errorMessage
) {
    public static PaymentCompletedEvent success(
        UUID paymentId, UUID senderAccountId, UUID receiverAccountId, BigDecimal amount, String currency) {

        return new PaymentCompletedEvent(
            paymentId, senderAccountId, receiverAccountId, amount, currency, PaymentStatus.COMPLETED, null, null
        );
    }

    public static PaymentCompletedEvent failure(
        UUID paymentId, UUID senderAccountId, UUID receiverAccountId, BigDecimal amount, String currency,
        ErrorCode errorCode, String errorMessage) {

        return new PaymentCompletedEvent(
            paymentId, senderAccountId, receiverAccountId, amount, currency, PaymentStatus.FAILED, errorCode, errorMessage
        );
    }
}
