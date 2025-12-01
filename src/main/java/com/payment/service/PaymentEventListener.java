package com.payment.service;

import com.payment.event.PaymentCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventListener {

    private final PaymentProcessor paymentProcessor;

    @ApplicationModuleListener
    public void onPaymentCreated(PaymentCreatedEvent event) {
        log.info("Received PaymentCreatedEvent for payment: {}", event.paymentId());
        paymentProcessor.process(event.paymentId());
    }
}
