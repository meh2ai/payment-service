package com.payment.temporal.activity;

import com.payment.config.TemporalConfig;
import com.payment.exception.ErrorCode;
import com.payment.exception.business.InsufficientBalanceException;
import com.payment.model.Account;
import com.payment.model.Payment;
import com.payment.model.PaymentStatus;
import com.payment.repository.AccountRepository;
import com.payment.repository.PaymentRepository;
import io.temporal.spring.boot.ActivityImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@ActivityImpl(taskQueues = TemporalConfig.PAYMENT_TASK_QUEUE)
@RequiredArgsConstructor
@Slf4j
public class LedgerActivitiesImpl implements LedgerActivities {

    private final PaymentRepository paymentRepository;
    private final AccountRepository accountRepository;

    @Override
    @Transactional
    public TransferResult executeTransfer(UUID paymentId) {
        log.info("Executing transfer for payment: {}", paymentId);

        Payment payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> new IllegalStateException("Payment not found: " + paymentId));

        if (payment.getStatus() != PaymentStatus.PENDING) {
            log.info("Payment {} already processed, status: {}", paymentId, payment.getStatus());
            return TransferResult.alreadyProcessed();
        }

        var accounts = loadAccountsWithLockOrdering(
            payment.getSenderAccountId(),
            payment.getReceiverAccountId()
        );
        Account sender = accounts.sender();
        Account receiver = accounts.receiver();

        // Validate accounts exist before any writes
        if (sender == null) {
            log.warn("Sender account not found: {}", payment.getSenderAccountId());
            return TransferResult.failure(ErrorCode.SENDER_ACCOUNT_NOT_FOUND,
                "Sender account not found: " + payment.getSenderAccountId());
        }

        if (receiver == null) {
            log.warn("Receiver account not found: {}", payment.getReceiverAccountId());
            return TransferResult.failure(ErrorCode.RECEIVER_ACCOUNT_NOT_FOUND,
                "Receiver account not found: " + payment.getReceiverAccountId());
        }

        // Proceed with transfer - Account.debit() validates sufficient balance
        payment.markProcessing();
        try {
            sender.debit(payment.getAmount());
        } catch (InsufficientBalanceException e) {
            log.warn(
                "Insufficient balance for payment {}: balance={}, amount={}",
                paymentId, e.getCurrentBalance(), e.getRequestedAmount()
            );
            return TransferResult.failure(ErrorCode.INSUFFICIENT_BALANCE,
                "Insufficient balance. Available: " + e.getCurrentBalance() + ", Required: " + e.getRequestedAmount());
        }
        receiver.credit(payment.getAmount());
        payment.markCompleted();

        log.info("Transfer successful for payment {}", paymentId);
        return TransferResult.success();
    }

    @Override
    @Transactional
    public void markPaymentFailed(UUID paymentId, String errorCodeName, String errorMessage) {
        log.info("Marking payment {} as FAILED", paymentId);

        Payment payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> new IllegalStateException("Payment not found: " + paymentId));

        if (payment.getStatus() == PaymentStatus.COMPLETED) {
            log.warn("Cannot mark payment {} as FAILED, already COMPLETED", paymentId);
            return;
        }

        ErrorCode errorCode = ErrorCode.valueOf(errorCodeName);
        payment.markFailed(errorCode, errorMessage);
    }

    /**
     * Loads both accounts with pessimistic locks in a consistent order to prevent deadlocks.
     * Always acquires locks in UUID order regardless of which is sender/receiver.
     */
    private AccountPair loadAccountsWithLockOrdering(UUID senderId, UUID receiverId) {
        boolean senderFirst = senderId.compareTo(receiverId) < 0;

        Account first = accountRepository.findByIdWithLock(senderFirst ? senderId : receiverId).orElse(null);
        Account second = accountRepository.findByIdWithLock(senderFirst ? receiverId : senderId).orElse(null);

        return senderFirst
            ? new AccountPair(first, second)
            : new AccountPair(second, first);
    }

    private record AccountPair(Account sender, Account receiver) {
    }
}
