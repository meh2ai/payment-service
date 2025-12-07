package com.payment.temporal.activity;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

import java.util.UUID;

@ActivityInterface
public interface NotificationActivities {

    @ActivityMethod
    void publishCompletionEvent(UUID paymentId);
}
