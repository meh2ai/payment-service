package com.payment.temporal.workflow;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

import java.util.UUID;

@WorkflowInterface
public interface PaymentWorkflow {

    @WorkflowMethod
    void processPayment(UUID paymentId);
}
