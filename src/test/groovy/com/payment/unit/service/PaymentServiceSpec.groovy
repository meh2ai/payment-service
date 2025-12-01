package com.payment.unit.service

import com.payment.api.model.PaymentRequest
import com.payment.api.model.PaymentStatus
import com.payment.event.PaymentCreatedEvent
import com.payment.exception.ErrorCode
import com.payment.exception.PaymentException
import com.payment.model.Payment
import com.payment.repository.AccountRepository
import com.payment.repository.PaymentRepository
import com.payment.service.PaymentService
import org.modelmapper.ModelMapper
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import spock.lang.Specification
import spock.lang.Subject

class PaymentServiceSpec extends Specification {

    PaymentRepository paymentRepository = Mock()
    AccountRepository accountRepository = Mock()
    ApplicationEventPublisher eventPublisher = Mock()
    ModelMapper modelMapper = new ModelMapper()

    @Subject
    PaymentService paymentService = new PaymentService(paymentRepository, accountRepository, eventPublisher, modelMapper)

    def senderAccountId = UUID.randomUUID()
    def receiverAccountId = UUID.randomUUID()

    def "should create new payment and publish event"() {
        given:
        def request = new PaymentRequest()
        request.setSenderAccountId(senderAccountId)
        request.setReceiverAccountId(receiverAccountId)
        request.setAmount("100.00")
        request.setCurrency("EUR")

        def idempotencyKey = "test-key"

        when:
        def response = paymentService.submitPayment(request, idempotencyKey)

        then:
        1 * paymentRepository.findByIdempotencyKey(idempotencyKey) >> Optional.empty()
        1 * accountRepository.existsById(senderAccountId) >> true
        1 * accountRepository.existsById(receiverAccountId) >> true
        1 * paymentRepository.save(_ as Payment)
        1 * eventPublisher.publishEvent(_ as PaymentCreatedEvent)

        and:
        response.paymentId != null
        response.status == PaymentStatus.PENDING
    }

    def "should return existing payment for duplicate idempotency key"() {
        given:
        def request = new PaymentRequest(senderAccountId, receiverAccountId, "100.00", "EUR")

        def idempotencyKey = "duplicate-key"
        def existingPayment = Payment.create(idempotencyKey, senderAccountId, receiverAccountId, new BigDecimal("100.00"), "EUR")

        when:
        def response = paymentService.submitPayment(request, idempotencyKey)

        then:
        1 * paymentRepository.findByIdempotencyKey(idempotencyKey) >> Optional.of(existingPayment)
        0 * accountRepository.existsById(_)
        0 * paymentRepository.save(_)
        0 * eventPublisher.publishEvent(_)

        and:
        response.paymentId == existingPayment.id
    }

    def "should throw exception when sender account not found"() {
        given:
        def request = new PaymentRequest(senderAccountId, receiverAccountId, "100.00", "EUR")
        def idempotencyKey = "test-key"

        when:
        paymentService.submitPayment(request, idempotencyKey)

        then:
        1 * paymentRepository.findByIdempotencyKey(idempotencyKey) >> Optional.empty()
        1 * accountRepository.existsById(senderAccountId) >> false
        0 * paymentRepository.save(_)
        0 * eventPublisher.publishEvent(_)

        and:
        def ex = thrown(PaymentException)
        ex.errorCode == ErrorCode.SENDER_ACCOUNT_NOT_FOUND
    }

    def "should throw exception when receiver account not found"() {
        given:
        def request = new PaymentRequest(senderAccountId, receiverAccountId, "100.00", "EUR")
        def idempotencyKey = "test-key"

        when:
        paymentService.submitPayment(request, idempotencyKey)

        then:
        1 * paymentRepository.findByIdempotencyKey(idempotencyKey) >> Optional.empty()
        1 * accountRepository.existsById(senderAccountId) >> true
        1 * accountRepository.existsById(receiverAccountId) >> false
        0 * paymentRepository.save(_)
        0 * eventPublisher.publishEvent(_)

        and:
        def ex = thrown(PaymentException)
        ex.errorCode == ErrorCode.RECEIVER_ACCOUNT_NOT_FOUND
    }

    def "should throw exception when sender and receiver are the same"() {
        given:
        def request = new PaymentRequest(senderAccountId, senderAccountId, "100.00", "EUR")
        def idempotencyKey = "test-key"

        when:
        paymentService.submitPayment(request, idempotencyKey)

        then:
        1 * paymentRepository.findByIdempotencyKey(idempotencyKey) >> Optional.empty()
        0 * accountRepository.existsById(_)
        0 * paymentRepository.save(_)
        0 * eventPublisher.publishEvent(_)

        and:
        def ex = thrown(PaymentException)
        ex.errorCode == ErrorCode.SAME_ACCOUNT
    }

    def "should throw exception when amount is zero"() {
        given:
        def request = new PaymentRequest(senderAccountId, receiverAccountId, "0.00", "EUR")
        def idempotencyKey = "test-key"

        when:
        paymentService.submitPayment(request, idempotencyKey)

        then:
        1 * paymentRepository.findByIdempotencyKey(idempotencyKey) >> Optional.empty()
        1 * accountRepository.existsById(senderAccountId) >> true
        1 * accountRepository.existsById(receiverAccountId) >> true
        0 * paymentRepository.save(_)
        0 * eventPublisher.publishEvent(_)

        and:
        def ex = thrown(PaymentException)
        ex.errorCode == ErrorCode.INVALID_AMOUNT
    }

    def "should throw exception when amount is negative"() {
        given:
        def request = new PaymentRequest(senderAccountId, receiverAccountId, "-100.00", "EUR")
        def idempotencyKey = "test-key"

        when:
        paymentService.submitPayment(request, idempotencyKey)

        then:
        1 * paymentRepository.findByIdempotencyKey(idempotencyKey) >> Optional.empty()
        1 * accountRepository.existsById(senderAccountId) >> true
        1 * accountRepository.existsById(receiverAccountId) >> true
        0 * paymentRepository.save(_)
        0 * eventPublisher.publishEvent(_)

        and:
        def ex = thrown(PaymentException)
        ex.errorCode == ErrorCode.INVALID_AMOUNT
    }

    def "should get payment by id"() {
        given:
        def payment = Payment.create("key", senderAccountId, receiverAccountId, new BigDecimal("50.00"), "EUR")

        when:
        def response = paymentService.getPayment(payment.id)

        then:
        1 * paymentRepository.findById(payment.id) >> Optional.of(payment)

        and:
        response.paymentId == payment.id
        response.amount == "50.00"
        response.currency == "EUR"
    }

    def "should throw PaymentException when payment not found"() {
        given:
        def paymentId = UUID.randomUUID()
        paymentRepository.findById(paymentId) >> Optional.empty()

        when:
        paymentService.getPayment(paymentId)

        then:
        def ex = thrown(PaymentException)
        ex.errorCode == ErrorCode.PAYMENT_NOT_FOUND
    }

    def "should list payments with specification"() {
        given:
        def pageable = PageRequest.of(0, 20)
        def payment = Payment.create("key", senderAccountId, receiverAccountId, new BigDecimal("100.00"), "EUR")
        def page = new PageImpl<>([payment], pageable, 1)

        when:
        def response = paymentService.listPayments(senderAccountId, null, pageable)

        then:
        1 * paymentRepository.findAll(_, pageable) >> page

        and:
        response.content.size() == 1
        response.totalElements == 1
    }

    def "should list payments filtered by status"() {
        given:
        def pageable = PageRequest.of(0, 20)
        def payment = Payment.create("key", senderAccountId, receiverAccountId, new BigDecimal("100.00"), "EUR")
        payment.markCompleted()
        def page = new PageImpl<>([payment], pageable, 1)

        when:
        def response = paymentService.listPayments(null, com.payment.model.PaymentStatus.COMPLETED, pageable)

        then:
        1 * paymentRepository.findAll(_, pageable) >> page

        and:
        response.content.size() == 1
        response.content[0].status == PaymentStatus.COMPLETED
    }


    def "should return empty list when no payments match"() {
        given:
        def pageable = PageRequest.of(0, 20)
        def page = new PageImpl<>([], pageable, 0)

        when:
        def response = paymentService.listPayments(UUID.randomUUID(), null, pageable)

        then:
        1 * paymentRepository.findAll(_, pageable) >> page

        and:
        response.content.isEmpty()
        response.totalElements == 0
    }
}
