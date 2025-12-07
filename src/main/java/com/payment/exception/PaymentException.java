package com.payment.exception;

import lombok.Getter;

import java.util.UUID;

@Getter
public class PaymentException extends RuntimeException {

    private final ErrorCode errorCode;

    public PaymentException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public static PaymentException duplicatePayment(String idempotencyKey) {
        return new PaymentException(
            ErrorCode.DUPLICATE_PAYMENT,
            "Duplicate payment with idempotency key: " + idempotencyKey
        );
    }

    public static PaymentException processingFailed(UUID paymentId, String reason) {
        return new PaymentException(
            ErrorCode.PAYMENT_PROCESSING_FAILED,
            "Payment " + paymentId + " processing failed: " + reason
        );
    }
}
