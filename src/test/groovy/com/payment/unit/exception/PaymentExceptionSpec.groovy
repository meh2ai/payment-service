package com.payment.unit.exception

import com.payment.exception.ErrorCode
import com.payment.exception.PaymentException
import spock.lang.Specification

class PaymentExceptionSpec extends Specification {

    def "paymentNotFound should create exception with correct error code"() {
        given:
        def paymentId = UUID.randomUUID()

        when:
        def exception = PaymentException.paymentNotFound(paymentId)

        then:
        exception.errorCode == ErrorCode.PAYMENT_NOT_FOUND
        exception.message.contains(paymentId.toString())
    }

    def "accountNotFound should create exception with correct error code"() {
        given:
        def accountId = UUID.randomUUID()

        when:
        def exception = PaymentException.accountNotFound(accountId)

        then:
        exception.errorCode == ErrorCode.ACCOUNT_NOT_FOUND
        exception.message.contains(accountId.toString())
    }

    def "senderAccountNotFound should create exception with correct error code"() {
        given:
        def accountId = UUID.randomUUID()

        when:
        def exception = PaymentException.senderAccountNotFound(accountId)

        then:
        exception.errorCode == ErrorCode.SENDER_ACCOUNT_NOT_FOUND
        exception.message.contains(accountId.toString())
    }

    def "receiverAccountNotFound should create exception with correct error code"() {
        given:
        def accountId = UUID.randomUUID()

        when:
        def exception = PaymentException.receiverAccountNotFound(accountId)

        then:
        exception.errorCode == ErrorCode.RECEIVER_ACCOUNT_NOT_FOUND
        exception.message.contains(accountId.toString())
    }

    def "insufficientBalance should create exception with balance details"() {
        given:
        def accountId = UUID.randomUUID()
        def currentBalance = new BigDecimal("50.00")
        def requestedAmount = new BigDecimal("100.00")

        when:
        def exception = PaymentException.insufficientBalance(accountId, currentBalance, requestedAmount)

        then:
        exception.errorCode == ErrorCode.INSUFFICIENT_BALANCE
        exception.message.contains(accountId.toString())
        exception.message.contains("50.00")
        exception.message.contains("100.00")
    }

    def "duplicatePayment should create exception with idempotency key"() {
        given:
        def idempotencyKey = "test-key-123"

        when:
        def exception = PaymentException.duplicatePayment(idempotencyKey)

        then:
        exception.errorCode == ErrorCode.DUPLICATE_PAYMENT
        exception.message.contains(idempotencyKey)
    }

    def "processingFailed should create exception with payment id and reason"() {
        given:
        def paymentId = UUID.randomUUID()
        def reason = "Network timeout"

        when:
        def exception = PaymentException.processingFailed(paymentId, reason)

        then:
        exception.errorCode == ErrorCode.PAYMENT_PROCESSING_FAILED
        exception.message.contains(paymentId.toString())
        exception.message.contains(reason)
    }
}
