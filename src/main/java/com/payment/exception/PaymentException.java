package com.payment.exception;

import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
public class PaymentException extends RuntimeException {

    private final ErrorCode errorCode;

    private PaymentException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public static PaymentException paymentNotFound(UUID paymentId) {
        return new PaymentException(
            ErrorCode.PAYMENT_NOT_FOUND,
            String.format("Payment not found: %s", paymentId)
        );
    }

    public static PaymentException accountNotFound(UUID accountId) {
        return new PaymentException(
            ErrorCode.ACCOUNT_NOT_FOUND,
            String.format("Account not found: %s", accountId)
        );
    }

    public static PaymentException senderAccountNotFound(UUID accountId) {
        return new PaymentException(
            ErrorCode.SENDER_ACCOUNT_NOT_FOUND,
            String.format("Sender account not found: %s", accountId)
        );
    }

    public static PaymentException receiverAccountNotFound(UUID accountId) {
        return new PaymentException(
            ErrorCode.RECEIVER_ACCOUNT_NOT_FOUND,
            String.format("Receiver account not found: %s", accountId)
        );
    }

    public static PaymentException insufficientBalance(UUID accountId, BigDecimal currentBalance, BigDecimal requestedAmount) {
        return new PaymentException(
            ErrorCode.INSUFFICIENT_BALANCE,
            String.format("Account %s has insufficient balance. Current: %s, Requested: %s",
                accountId, currentBalance, requestedAmount)
        );
    }

    public static PaymentException duplicatePayment(String idempotencyKey) {
        return new PaymentException(
            ErrorCode.DUPLICATE_PAYMENT,
            String.format("Duplicate payment with idempotency key: %s", idempotencyKey)
        );
    }

    public static PaymentException processingFailed(UUID paymentId, String reason) {
        return new PaymentException(
            ErrorCode.PAYMENT_PROCESSING_FAILED,
            String.format("Payment %s processing failed: %s", paymentId, reason)
        );
    }

    public static PaymentException invalidAmount(BigDecimal amount) {
        return new PaymentException(
            ErrorCode.INVALID_AMOUNT,
            String.format("Invalid payment amount: %s. Amount must be greater than zero", amount)
        );
    }

    public static PaymentException sameAccount(UUID accountId) {
        return new PaymentException(
            ErrorCode.SAME_ACCOUNT,
            String.format("Sender and receiver cannot be the same account: %s", accountId)
        );
    }
}
