package com.payment.temporal.activity;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

import java.util.UUID;

@ActivityInterface
public interface LedgerActivities {

    @ActivityMethod
    TransferResult executeTransfer(UUID paymentId);

    @ActivityMethod
    void markPaymentFailed(UUID paymentId, String errorCode, String errorMessage);
}
