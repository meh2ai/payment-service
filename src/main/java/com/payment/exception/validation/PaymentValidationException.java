package com.payment.exception.validation;

import com.payment.exception.ErrorCode;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
public class PaymentValidationException extends RuntimeException {

    private final ErrorCode errorCode;

    public PaymentValidationException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public static PaymentValidationException invalidAmount(BigDecimal amount) {
        return new PaymentValidationException(
            ErrorCode.INVALID_AMOUNT,
            String.format("Invalid payment amount: %s. Amount must be greater than zero", amount)
        );
    }

    public static PaymentValidationException sameAccount(UUID accountId) {
        return new PaymentValidationException(
            ErrorCode.SAME_ACCOUNT,
            String.format("Sender and receiver cannot be the same account: %s", accountId)
        );
    }
}
