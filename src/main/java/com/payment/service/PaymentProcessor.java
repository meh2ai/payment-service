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
import java.util.function.Function;

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
            UUID senderId = payment.getSenderAccountId();
            UUID receiverId = payment.getReceiverAccountId();

            Account sender;
            Account receiver;

            // Lock ordering to prevent deadlocks
            if (senderId.compareTo(receiverId) < 0) {
                sender = getAccountWithLock(senderId, PaymentException::senderAccountNotFound);
                receiver = getAccountWithLock(receiverId, PaymentException::receiverAccountNotFound);
            } else {
                receiver = getAccountWithLock(receiverId, PaymentException::receiverAccountNotFound);
                sender = getAccountWithLock(senderId, PaymentException::senderAccountNotFound);
            }

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

            log.error("Payment {} failed", paymentId, e);

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

    private Account getAccountWithLock(UUID id, Function<UUID, RuntimeException> exceptionSupplier) {
        return accountRepository.findByIdWithLock(id).orElseThrow(() -> exceptionSupplier.apply(id));
    }
}
