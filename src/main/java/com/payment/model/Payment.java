package com.payment.model;

import com.payment.exception.ErrorCode;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor
public class Payment {

    @Id
    private UUID id;

    private String idempotencyKey;

    private UUID senderAccountId;

    private UUID receiverAccountId;

    private BigDecimal amount;

    private String currency;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    @Enumerated(EnumType.STRING)
    private ErrorCode errorCode;

    private String errorMessage;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @Version
    private Long version;

    public static Payment create(
        String idempotencyKey, UUID senderAccountId, UUID receiverAccountId, BigDecimal amount, String currency) {

        Payment payment = new Payment();
        payment.id = UUID.randomUUID();
        payment.idempotencyKey = idempotencyKey;
        payment.senderAccountId = senderAccountId;
        payment.receiverAccountId = receiverAccountId;
        payment.amount = amount;
        payment.currency = currency;
        payment.status = PaymentStatus.PENDING;
        return payment;
    }

    public void markProcessing() {
        this.status = PaymentStatus.PROCESSING;
    }

    public void markCompleted() {
        this.status = PaymentStatus.COMPLETED;
    }

    public void markFailed(ErrorCode errorCode, String errorMessage) {
        this.status = PaymentStatus.FAILED;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }
}
