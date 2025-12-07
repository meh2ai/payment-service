package com.payment.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Payment errors (1xxx)
    PAYMENT_NOT_FOUND(1001, HttpStatus.NOT_FOUND),
    DUPLICATE_PAYMENT(1002, HttpStatus.CONFLICT),
    PAYMENT_PROCESSING_FAILED(1003, HttpStatus.UNPROCESSABLE_ENTITY),

    // Account errors (2xxx)
    ACCOUNT_NOT_FOUND(2001, HttpStatus.NOT_FOUND),
    SENDER_ACCOUNT_NOT_FOUND(2002, HttpStatus.NOT_FOUND),
    RECEIVER_ACCOUNT_NOT_FOUND(2003, HttpStatus.NOT_FOUND),
    INSUFFICIENT_BALANCE(2004, HttpStatus.UNPROCESSABLE_ENTITY),
    SAME_ACCOUNT(2005, HttpStatus.BAD_REQUEST),

    // Validation errors (3xxx)
    VALIDATION_ERROR(3001, HttpStatus.BAD_REQUEST),
    INVALID_AMOUNT(3002, HttpStatus.BAD_REQUEST),

    // System errors (5xxx)
    INTERNAL_ERROR(5001, HttpStatus.INTERNAL_SERVER_ERROR);

    private final int numericCode;
    private final HttpStatus httpStatus;
}
