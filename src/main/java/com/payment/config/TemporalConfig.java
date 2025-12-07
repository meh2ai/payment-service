package com.payment.config;

/**
 * Temporal configuration.
 * The WorkflowClient, WorkerFactory, and Workers are auto-configured by the temporal-spring-boot-starter based on application.yml settings.
 */
public final class TemporalConfig {

    public static final String PAYMENT_TASK_QUEUE = "PAYMENT_TASK_QUEUE";

    private TemporalConfig() {
    }
}
