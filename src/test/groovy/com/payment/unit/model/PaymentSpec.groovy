package com.payment.unit.model

import com.payment.exception.ErrorCode
import com.payment.model.Payment
import com.payment.model.PaymentStatus
import spock.lang.Specification

class PaymentSpec extends Specification {

    def senderAccountId = UUID.randomUUID()
    def receiverAccountId = UUID.randomUUID()

    def "should create payment with PENDING status"() {
        when:
        def payment = Payment.create("key-123", senderAccountId, receiverAccountId,
            new BigDecimal("100.00"), "EUR")

        then:
        payment.id != null
        payment.idempotencyKey == "key-123"
        payment.senderAccountId == senderAccountId
        payment.receiverAccountId == receiverAccountId
        payment.amount == new BigDecimal("100.00")
        payment.currency == "EUR"
        payment.status == PaymentStatus.PENDING
        payment.errorCode == null
        payment.errorMessage == null
    }

    def "should generate unique IDs for different payments"() {
        when:
        def payment1 = Payment.create("key-1", senderAccountId, receiverAccountId,
            new BigDecimal("100.00"), "EUR")
        def payment2 = Payment.create("key-2", senderAccountId, receiverAccountId,
            new BigDecimal("100.00"), "EUR")

        then:
        payment1.id != payment2.id
    }

    def "should transition to PROCESSING status"() {
        given:
        def payment = Payment.create("key", senderAccountId, receiverAccountId,
            new BigDecimal("100.00"), "EUR")

        when:
        payment.markProcessing()

        then:
        payment.status == PaymentStatus.PROCESSING
        payment.errorCode == null
        payment.errorMessage == null
    }

    def "should transition to COMPLETED status"() {
        given:
        def payment = Payment.create("key", senderAccountId, receiverAccountId,
            new BigDecimal("100.00"), "EUR")

        when:
        payment.markCompleted()

        then:
        payment.status == PaymentStatus.COMPLETED
        payment.errorCode == null
        payment.errorMessage == null
    }

    def "should transition to FAILED status with error details"() {
        given:
        def payment = Payment.create("key", senderAccountId, receiverAccountId,
            new BigDecimal("100.00"), "EUR")

        when:
        payment.markFailed(ErrorCode.INSUFFICIENT_BALANCE, "Not enough funds")

        then:
        payment.status == PaymentStatus.FAILED
        payment.errorCode == ErrorCode.INSUFFICIENT_BALANCE
        payment.errorMessage == "Not enough funds"
    }

    def "should allow full state transition: PENDING -> PROCESSING -> COMPLETED"() {
        given:
        def payment = Payment.create("key", senderAccountId, receiverAccountId,
            new BigDecimal("100.00"), "EUR")

        expect:
        payment.status == PaymentStatus.PENDING

        when:
        payment.markProcessing()

        then:
        payment.status == PaymentStatus.PROCESSING

        when:
        payment.markCompleted()

        then:
        payment.status == PaymentStatus.COMPLETED
    }

    def "should allow full state transition: PENDING -> PROCESSING -> FAILED"() {
        given:
        def payment = Payment.create("key", senderAccountId, receiverAccountId,
            new BigDecimal("100.00"), "EUR")

        expect:
        payment.status == PaymentStatus.PENDING

        when:
        payment.markProcessing()

        then:
        payment.status == PaymentStatus.PROCESSING

        when:
        payment.markFailed(ErrorCode.SENDER_ACCOUNT_NOT_FOUND, "Account not found")

        then:
        payment.status == PaymentStatus.FAILED
        payment.errorCode == ErrorCode.SENDER_ACCOUNT_NOT_FOUND
    }
}
