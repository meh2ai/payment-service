package com.payment.temporal.activity;

import com.payment.config.KafkaConfig;
import com.payment.config.TemporalConfig;
import com.payment.event.PaymentCompletedEvent;
import com.payment.model.Payment;
import com.payment.model.PaymentStatus;
import com.payment.repository.PaymentRepository;
import io.temporal.spring.boot.ActivityImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@ActivityImpl(taskQueues = TemporalConfig.PAYMENT_TASK_QUEUE)
@RequiredArgsConstructor
@Slf4j
public class NotificationActivitiesImpl implements NotificationActivities {

    private final PaymentRepository paymentRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    @Transactional(readOnly = true)
    public void publishCompletionEvent(UUID paymentId) {
        log.info("Publishing completion event for payment: {}", paymentId);
        
        Payment payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> new IllegalStateException("Payment not found: " + paymentId));

        PaymentCompletedEvent event;

        if (payment.getStatus() == PaymentStatus.COMPLETED) {
            event = PaymentCompletedEvent.success(
                payment.getId(),
                payment.getSenderAccountId(),
                payment.getReceiverAccountId(),
                payment.getAmount(),
                payment.getCurrency()
            );
        } else if (payment.getStatus() == PaymentStatus.FAILED) {
            event = PaymentCompletedEvent.failure(
                payment.getId(),
                payment.getSenderAccountId(),
                payment.getReceiverAccountId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getErrorCode(),
                payment.getErrorMessage()
            );
        } else {
            log.warn("Attempted to publish event for payment {} in status {}", paymentId, payment.getStatus());
            return;
        }

        kafkaTemplate.send(KafkaConfig.PAYMENT_NOTIFICATIONS_TOPIC, paymentId.toString(), event);
    }
}
