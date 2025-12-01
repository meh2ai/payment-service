package com.payment.event;

import com.payment.exception.ErrorCode;
import com.payment.model.PaymentStatus;

import java.time.Instant;
import java.util.UUID;

public record PaymentNotification(
    UUID paymentId,
    UUID senderAccountId,
    UUID receiverAccountId,
    String amount,
    String currency,
    PaymentStatus status,
    ErrorCode errorCode,
    Integer numericErrorCode,
    String errorMessage,
    Instant timestamp
) {
    public static PaymentNotification fromCompletedEvent(PaymentCompletedEvent event) {
        return new PaymentNotification(
            event.paymentId(),
            event.senderAccountId(),
            event.receiverAccountId(),
            event.amount().toPlainString(),
            event.currency(),
            event.status(),
            event.errorCode(),
            event.errorCode() != null ? event.errorCode().getNumericCode() : null,
            event.errorMessage(),
            Instant.now()
        );
    }
}
