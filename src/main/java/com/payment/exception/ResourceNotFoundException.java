package com.payment.exception;

import lombok.Getter;

import java.util.UUID;

@Getter
public class ResourceNotFoundException extends RuntimeException {

    private final ErrorCode errorCode;

    public ResourceNotFoundException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public static ResourceNotFoundException paymentNotFound(UUID paymentId) {
        return new ResourceNotFoundException(
            ErrorCode.PAYMENT_NOT_FOUND,
            "Payment not found: " + paymentId
        );
    }

    public static ResourceNotFoundException accountNotFound(UUID accountId) {
        return new ResourceNotFoundException(
            ErrorCode.ACCOUNT_NOT_FOUND,
            "Account not found: " + accountId
        );
    }

    public static ResourceNotFoundException senderAccountNotFound(UUID accountId) {
        return new ResourceNotFoundException(
            ErrorCode.SENDER_ACCOUNT_NOT_FOUND,
            "Sender account not found: " + accountId
        );
    }

    public static ResourceNotFoundException receiverAccountNotFound(UUID accountId) {
        return new ResourceNotFoundException(
            ErrorCode.RECEIVER_ACCOUNT_NOT_FOUND,
            "Receiver account not found: " + accountId
        );
    }
}
