package com.payment.integration.repository

import com.payment.exception.ErrorCode
import com.payment.integration.IntegrationTestBase
import com.payment.model.Account
import com.payment.model.Payment
import com.payment.model.PaymentStatus
import com.payment.repository.AccountRepository
import com.payment.repository.PaymentRepository
import com.payment.repository.PaymentSpecification
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort

class PaymentRepositorySpec extends IntegrationTestBase {

    @Autowired
    PaymentRepository paymentRepository

    @Autowired
    AccountRepository accountRepository

    UUID senderAccountId
    UUID receiverAccountId

    def setup() {
        senderAccountId = UUID.randomUUID()
        receiverAccountId = UUID.randomUUID()
        accountRepository.save(new Account(senderAccountId, new BigDecimal("1000.00"), "EUR"))
        accountRepository.save(new Account(receiverAccountId, new BigDecimal("500.00"), "EUR"))
    }

    def cleanup() {
        paymentRepository.deleteAll()
        accountRepository.deleteAll()
    }

    def "should save and retrieve payment"() {
        given:
        def payment = Payment.create("key-123", senderAccountId, receiverAccountId,
                new BigDecimal("100.00"), "EUR")

        when:
        paymentRepository.save(payment)
        def retrieved = paymentRepository.findById(payment.id)

        then:
        retrieved.isPresent()
        retrieved.get().idempotencyKey == "key-123"
        retrieved.get().senderAccountId == senderAccountId
        retrieved.get().status == PaymentStatus.PENDING
        retrieved.get().createdAt != null
        retrieved.get().updatedAt != null
    }

    def "should find payment by idempotency key"() {
        given:
        def payment = Payment.create("unique-key", senderAccountId, receiverAccountId,
                new BigDecimal("50.00"), "EUR")
        paymentRepository.save(payment)

        when:
        def found = paymentRepository.findByIdempotencyKey("unique-key")

        then:
        found.isPresent()
        found.get().id == payment.id
    }

    def "should return empty for non-existent idempotency key"() {
        when:
        def result = paymentRepository.findByIdempotencyKey("non-existent")

        then:
        !result.isPresent()
    }

    def "should enforce unique idempotency key constraint"() {
        given:
        def payment1 = Payment.create("duplicate-key", senderAccountId, receiverAccountId,
                new BigDecimal("100.00"), "EUR")
        def payment2 = Payment.create("duplicate-key", senderAccountId, receiverAccountId,
                new BigDecimal("200.00"), "EUR")

        when:
        paymentRepository.saveAndFlush(payment1)
        paymentRepository.saveAndFlush(payment2)

        then:
        thrown(DataIntegrityViolationException)
    }

    def "should find payments by sender account using specification"() {
        given:
        def otherSender = UUID.randomUUID()
        accountRepository.save(new Account(otherSender, new BigDecimal("100.00"), "EUR"))

        paymentRepository.save(Payment.create("key-1", senderAccountId, receiverAccountId,
                new BigDecimal("100.00"), "EUR"))
        paymentRepository.save(Payment.create("key-2", senderAccountId, receiverAccountId,
                new BigDecimal("200.00"), "EUR"))
        paymentRepository.save(Payment.create("key-3", otherSender, receiverAccountId,
                new BigDecimal("300.00"), "EUR"))

        when:
        def spec = PaymentSpecification.searchBy(senderAccountId, null)
        def result = paymentRepository.findAll(spec, PageRequest.of(0, 10))

        then:
        result.content.size() == 2
        result.content.every { it.senderAccountId == senderAccountId }
    }

    def "should find payments by status using specification"() {
        given:
        def payment1 = Payment.create("key-1", senderAccountId, receiverAccountId,
                new BigDecimal("100.00"), "EUR")
        def payment2 = Payment.create("key-2", senderAccountId, receiverAccountId,
                new BigDecimal("200.00"), "EUR")
        payment2.markProcessing()
        payment2.markCompleted()

        paymentRepository.save(payment1)
        paymentRepository.save(payment2)

        when:
        def pendingSpec = PaymentSpecification.searchBy(null, PaymentStatus.PENDING)
        def completedSpec = PaymentSpecification.searchBy(null, PaymentStatus.COMPLETED)
        def pendingPayments = paymentRepository.findAll(pendingSpec, PageRequest.of(0, 10))
        def completedPayments = paymentRepository.findAll(completedSpec, PageRequest.of(0, 10))

        then:
        pendingPayments.content.size() == 1
        completedPayments.content.size() == 1
    }

    def "should find payments by sender and status using specification"() {
        given:
        def otherSender = UUID.randomUUID()
        accountRepository.save(new Account(otherSender, new BigDecimal("100.00"), "EUR"))

        def payment1 = Payment.create("key-1", senderAccountId, receiverAccountId,
                new BigDecimal("100.00"), "EUR")
        payment1.markProcessing()
        payment1.markCompleted()

        def payment2 = Payment.create("key-2", senderAccountId, receiverAccountId,
                new BigDecimal("200.00"), "EUR")

        def payment3 = Payment.create("key-3", otherSender, receiverAccountId,
                new BigDecimal("300.00"), "EUR")
        payment3.markProcessing()
        payment3.markCompleted()

        paymentRepository.saveAll([payment1, payment2, payment3])

        when:
        def spec = PaymentSpecification.searchBy(senderAccountId, PaymentStatus.COMPLETED)
        def result = paymentRepository.findAll(spec, PageRequest.of(0, 10))

        then:
        result.content.size() == 1
        result.content[0].senderAccountId == senderAccountId
        result.content[0].status == PaymentStatus.COMPLETED
    }

    def "should return all payments when no filters provided"() {
        given:
        paymentRepository.save(Payment.create("key-1", senderAccountId, receiverAccountId,
                new BigDecimal("100.00"), "EUR"))
        paymentRepository.save(Payment.create("key-2", senderAccountId, receiverAccountId,
                new BigDecimal("200.00"), "EUR"))

        when:
        def spec = PaymentSpecification.searchBy(null, null)
        def result = paymentRepository.findAll(spec, PageRequest.of(0, 10))

        then:
        result.content.size() == 2
    }

    def "should paginate results"() {
        given:
        (1..25).each { i ->
            paymentRepository.save(Payment.create("key-$i", senderAccountId, receiverAccountId,
                    new BigDecimal("$i"), "EUR"))
        }

        when:
        def spec = PaymentSpecification.searchBy(null, null)
        def page1 = paymentRepository.findAll(spec, PageRequest.of(0, 10))
        def page2 = paymentRepository.findAll(spec, PageRequest.of(1, 10))
        def page3 = paymentRepository.findAll(spec, PageRequest.of(2, 10))

        then:
        page1.content.size() == 10
        page1.totalElements == 25
        page1.totalPages == 3
        page2.content.size() == 10
        page3.content.size() == 5
    }

    def "should sort results"() {
        given:
        paymentRepository.save(Payment.create("key-1", senderAccountId, receiverAccountId,
                new BigDecimal("300.00"), "EUR"))
        paymentRepository.save(Payment.create("key-2", senderAccountId, receiverAccountId,
                new BigDecimal("100.00"), "EUR"))
        paymentRepository.save(Payment.create("key-3", senderAccountId, receiverAccountId,
                new BigDecimal("200.00"), "EUR"))

        when:
        def spec = PaymentSpecification.searchBy(null, null)
        def resultAsc = paymentRepository.findAll(spec, PageRequest.of(0, 10, Sort.by("amount").ascending()))
        def resultDesc = paymentRepository.findAll(spec, PageRequest.of(0, 10, Sort.by("amount").descending()))

        then:
        resultAsc.content[0].amount == new BigDecimal("100.00")
        resultAsc.content[2].amount == new BigDecimal("300.00")
        resultDesc.content[0].amount == new BigDecimal("300.00")
        resultDesc.content[2].amount == new BigDecimal("100.00")
    }

    def "should store error code and message on failure"() {
        given:
        def payment = Payment.create("key-fail", senderAccountId, receiverAccountId,
                new BigDecimal("100.00"), "EUR")
        payment.markProcessing()
        payment.markFailed(ErrorCode.INSUFFICIENT_BALANCE, "Not enough funds")
        paymentRepository.save(payment)

        when:
        def retrieved = paymentRepository.findById(payment.id).get()

        then:
        retrieved.status == PaymentStatus.FAILED
        retrieved.errorCode == ErrorCode.INSUFFICIENT_BALANCE
        retrieved.errorMessage == "Not enough funds"
    }
}
