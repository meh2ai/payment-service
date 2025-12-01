package com.payment.unit.service

import com.payment.event.PaymentCompletedEvent
import com.payment.exception.ErrorCode
import com.payment.model.Account
import com.payment.model.Payment
import com.payment.model.PaymentStatus
import com.payment.repository.AccountRepository
import com.payment.repository.PaymentRepository
import com.payment.service.PaymentProcessor
import org.springframework.context.ApplicationEventPublisher
import spock.lang.Specification
import spock.lang.Subject

import java.time.Instant

class PaymentProcessorSpec extends Specification {

    PaymentRepository paymentRepository = Mock()
    AccountRepository accountRepository = Mock()
    ApplicationEventPublisher eventPublisher = Mock()

    @Subject
    PaymentProcessor paymentProcessor = new PaymentProcessor(
            paymentRepository, accountRepository, eventPublisher
    )

    def senderAccountId = UUID.randomUUID()
    def receiverAccountId = UUID.randomUUID()

    def "should process payment successfully"() {
        given:
        def payment = createTestPayment()
        def sender = new Account(senderAccountId, new BigDecimal("500.00"), "EUR")
        def receiver = new Account(receiverAccountId, new BigDecimal("200.00"), "EUR")

        when:
        paymentProcessor.process(payment.id)

        then:
        1 * paymentRepository.findById(payment.id) >> Optional.of(payment)
        1 * accountRepository.findById(senderAccountId) >> Optional.of(sender)
        1 * accountRepository.findById(receiverAccountId) >> Optional.of(receiver)
        2 * paymentRepository.save(_ as Payment)
        1 * accountRepository.save({ it.balance == new BigDecimal("400.00") })
        1 * accountRepository.save({ it.balance == new BigDecimal("300.00") })
        1 * eventPublisher.publishEvent({ PaymentCompletedEvent e ->
            e.status() == PaymentStatus.COMPLETED && e.errorCode() == null
        })
    }

    def "should fail payment when insufficient balance"() {
        given:
        def payment = createTestPayment()
        def sender = new Account(senderAccountId, new BigDecimal("50.00"), "EUR")
        def receiver = new Account(receiverAccountId, new BigDecimal("200.00"), "EUR")

        when:
        paymentProcessor.process(payment.id)

        then:
        1 * paymentRepository.findById(payment.id) >> Optional.of(payment)
        1 * accountRepository.findById(senderAccountId) >> Optional.of(sender)
        1 * accountRepository.findById(receiverAccountId) >> Optional.of(receiver)
        2 * paymentRepository.save(_ as Payment)
        1 * eventPublisher.publishEvent({ PaymentCompletedEvent e ->
            e.status() == PaymentStatus.FAILED &&
                    e.errorCode() == ErrorCode.INSUFFICIENT_BALANCE
        })
    }

    def "should fail payment when sender account not found"() {
        given:
        def payment = createTestPayment()

        when:
        paymentProcessor.process(payment.id)

        then:
        1 * paymentRepository.findById(payment.id) >> Optional.of(payment)
        1 * accountRepository.findById(senderAccountId) >> Optional.empty()
        2 * paymentRepository.save(_ as Payment)
        1 * eventPublisher.publishEvent({ PaymentCompletedEvent e ->
            e.status() == PaymentStatus.FAILED &&
                    e.errorCode() == ErrorCode.SENDER_ACCOUNT_NOT_FOUND
        })
    }

    def "should fail payment when receiver account not found"() {
        given:
        def payment = createTestPayment()
        def sender = new Account(senderAccountId, new BigDecimal("500.00"), "EUR")

        when:
        paymentProcessor.process(payment.id)

        then:
        1 * paymentRepository.findById(payment.id) >> Optional.of(payment)
        1 * accountRepository.findById(senderAccountId) >> Optional.of(sender)
        1 * accountRepository.findById(receiverAccountId) >> Optional.empty()
        2 * paymentRepository.save(_ as Payment)
        1 * eventPublisher.publishEvent({ PaymentCompletedEvent e ->
            e.status() == PaymentStatus.FAILED &&
                    e.errorCode() == ErrorCode.RECEIVER_ACCOUNT_NOT_FOUND
        })
    }

    def "should skip already processed payment"() {
        given:
        def payment = createTestPayment()
        payment.markCompleted()

        when:
        paymentProcessor.process(payment.id)

        then:
        1 * paymentRepository.findById(payment.id) >> Optional.of(payment)
        0 * accountRepository.findById(_)
        0 * paymentRepository.save(_)
        0 * eventPublisher.publishEvent(_)
    }

    def "should skip payment in PROCESSING status"() {
        given:
        def payment = createTestPayment()
        payment.markProcessing()

        when:
        paymentProcessor.process(payment.id)

        then:
        1 * paymentRepository.findById(payment.id) >> Optional.of(payment)
        0 * accountRepository.findById(_)
        0 * eventPublisher.publishEvent(_)
    }

    def "should throw IllegalStateException when payment not found"() {
        given:
        def paymentId = UUID.randomUUID()

        when:
        paymentProcessor.process(paymentId)

        then:
        1 * paymentRepository.findById(paymentId) >> Optional.empty()
        thrown(IllegalStateException)
    }

    def createTestPayment() {
        def payment = Payment.create("key", senderAccountId, receiverAccountId, new BigDecimal("100.00"), "EUR")
        payment.createdAt = Instant.now()
        payment.updatedAt = Instant.now()
        return payment
    }
}
