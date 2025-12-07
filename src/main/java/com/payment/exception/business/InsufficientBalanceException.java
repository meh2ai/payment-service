package com.payment.exception.business;

import com.payment.exception.ErrorCode;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
public class InsufficientBalanceException extends RuntimeException {

    private final ErrorCode errorCode;
    private final BigDecimal currentBalance;
    private final BigDecimal requestedAmount;

    public InsufficientBalanceException(UUID accountId, BigDecimal currentBalance, BigDecimal requestedAmount) {
        super(String.format("Account %s has insufficient balance. Current: %s, Requested: %s", accountId, currentBalance, requestedAmount));
        this.errorCode = ErrorCode.INSUFFICIENT_BALANCE;
        this.currentBalance = currentBalance;
        this.requestedAmount = requestedAmount;
    }
}
