package com.payment.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Payment errors (1xxx)
    PAYMENT_NOT_FOUND(1001, "Payment not found"),
    DUPLICATE_PAYMENT(1002, "Duplicate payment request"),
    PAYMENT_PROCESSING_FAILED(1003, "Payment processing failed"),

    // Account errors (2xxx)
    ACCOUNT_NOT_FOUND(2001, "Account not found"),
    SENDER_ACCOUNT_NOT_FOUND(2002, "Sender account not found"),
    RECEIVER_ACCOUNT_NOT_FOUND(2003, "Receiver account not found"),
    INSUFFICIENT_BALANCE(2004, "Insufficient balance"),
    SAME_ACCOUNT(2005, "Sender and receiver accounts must be different"),

    // Validation errors (3xxx)
    VALIDATION_ERROR(3001, "Validation error"),
    INVALID_AMOUNT(3002, "Invalid amount"),
    INVALID_CURRENCY(3003, "Invalid currency"),

    // System errors (5xxx)
    INTERNAL_ERROR(5001, "Internal server error"),
    SERVICE_UNAVAILABLE(5002, "Service temporarily unavailable");

    private final int numericCode;
    private final String defaultMessage;
}
