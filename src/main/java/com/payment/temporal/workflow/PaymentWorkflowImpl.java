package com.payment.temporal.workflow;

import com.payment.config.TemporalConfig;
import com.payment.temporal.activity.LedgerActivities;
import com.payment.temporal.activity.NotificationActivities;
import com.payment.temporal.activity.TransferResult;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.Workflow;

import java.time.Duration;
import java.util.UUID;

@WorkflowImpl(taskQueues = TemporalConfig.PAYMENT_TASK_QUEUE)
public class PaymentWorkflowImpl implements PaymentWorkflow {

    private final LedgerActivities ledgerActivities = Workflow.newActivityStub(
        LedgerActivities.class,
        ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(10))
            .setRetryOptions(RetryOptions.newBuilder()
                .setMaximumAttempts(5)
                .setBackoffCoefficient(2.0)
                .build())
            .build()
    );

    private final NotificationActivities notificationActivities = Workflow.newActivityStub(
        NotificationActivities.class,
        ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(5))
            .setRetryOptions(RetryOptions.newBuilder()
                .setMaximumAttempts(10)
                .setBackoffCoefficient(2.0)
                .build())
            .build()
    );

    @Override
    public void processPayment(UUID paymentId) {
        TransferResult result = ledgerActivities.executeTransfer(paymentId);

        if (!result.successful()) {
            ledgerActivities.markPaymentFailed(paymentId, result.errorCode(), result.errorMessage());
        }

        notificationActivities.publishCompletionEvent(paymentId);
    }
}
