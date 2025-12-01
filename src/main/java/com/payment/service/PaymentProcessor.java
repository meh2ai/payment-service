package com.payment.service;

import com.payment.event.PaymentCompletedEvent;
import com.payment.exception.PaymentException;
import com.payment.model.Account;
import com.payment.model.Payment;
import com.payment.model.PaymentStatus;
import com.payment.repository.AccountRepository;
import com.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentProcessor {

    private final PaymentRepository paymentRepository;
    private final AccountRepository accountRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void process(UUID paymentId) {
        log.info("Processing Payment with id: {}", paymentId);
        Payment payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> new IllegalStateException("Payment not found: " + paymentId));

        if (payment.getStatus() != PaymentStatus.PENDING) {
            log.info("Payment {} already processed, status: {}", paymentId, payment.getStatus());
            return;
        }

        payment.markProcessing();
        paymentRepository.save(payment);

        try {
            Account sender = accountRepository.findById(payment.getSenderAccountId())
                .orElseThrow(() -> PaymentException.senderAccountNotFound(payment.getSenderAccountId()));

            Account receiver = accountRepository.findById(payment.getReceiverAccountId())
                .orElseThrow(() -> PaymentException.receiverAccountNotFound(payment.getReceiverAccountId()));

            sender.debit(payment.getAmount());
            receiver.credit(payment.getAmount());

            accountRepository.save(sender);
            accountRepository.save(receiver);

            payment.markCompleted();
            paymentRepository.save(payment);

            log.info("Payment {} completed successfully", paymentId);

            eventPublisher.publishEvent(PaymentCompletedEvent.success(
                payment.getId(),
                payment.getSenderAccountId(),
                payment.getReceiverAccountId(),
                payment.getAmount(),
                payment.getCurrency()
            ));

        } catch (PaymentException e) {
            payment.markFailed(e.getErrorCode(), e.getMessage());
            paymentRepository.save(payment);

            log.warn("Payment {} failed: {}", paymentId, e.getMessage());

            eventPublisher.publishEvent(PaymentCompletedEvent.failure(
                payment.getId(),
                payment.getSenderAccountId(),
                payment.getReceiverAccountId(),
                payment.getAmount(),
                payment.getCurrency(),
                e.getErrorCode(),
                e.getMessage()
            ));
        }
    }
}
