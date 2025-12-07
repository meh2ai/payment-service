package com.payment.temporal.activity;

import com.payment.exception.ErrorCode;

public record TransferResult(
    boolean successful,
    String errorCode,
    String errorMessage
) {
    public static TransferResult success() {
        return new TransferResult(true, null, null);
    }

    public static TransferResult failure(ErrorCode code, String message) {
        return new TransferResult(false, code.name(), message);
    }

    public static TransferResult alreadyProcessed() {
        return new TransferResult(true, null, null);
    }
}
