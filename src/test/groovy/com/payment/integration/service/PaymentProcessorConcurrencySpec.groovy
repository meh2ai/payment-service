package com.payment.integration.service

import com.payment.integration.IntegrationTestBase
import com.payment.model.Account
import com.payment.model.Payment
import com.payment.model.PaymentStatus
import com.payment.repository.AccountRepository
import com.payment.repository.PaymentRepository
import com.payment.service.PaymentProcessor
import org.springframework.beans.factory.annotation.Autowired
import java.util.concurrent.CompletableFuture

class PaymentProcessorConcurrencySpec extends IntegrationTestBase {

    @Autowired
    PaymentProcessor paymentProcessor

    @Autowired
    PaymentRepository paymentRepository

    @Autowired
    AccountRepository accountRepository

    def "should prevent double processing of the same payment"() {
        given: "a sender and receiver account"
        def sender = new Account(UUID.randomUUID(), new BigDecimal("1000.00"), "EUR")
        def receiver = new Account(UUID.randomUUID(), new BigDecimal("0.00"), "EUR")
        accountRepository.saveAll([sender, receiver])

        and: "a pending payment"
        def paymentAmount = new BigDecimal("100.00")
        def payment = Payment.create("req-1", sender.id, receiver.id, paymentAmount, "EUR")
        paymentRepository.save(payment)

        when: "two threads try to process the same payment concurrently"
        def futures = (1..2).collect {
            CompletableFuture.runAsync {
                try {
                    paymentProcessor.process(payment.id)
                } catch (Exception ignored) {
                    // Ignore exceptions (one might fail due to locking, but we care about result)
                }
            }
        }
        futures*.join()

        then: "the payment should be processed exactly once"
        def updatedSender = accountRepository.findById(sender.id).get()
        def updatedReceiver = accountRepository.findById(receiver.id).get()
        def updatedPayment = paymentRepository.findById(payment.id).get()

        // Expected: 1000 - 100 = 900. If processed twice: 800.
        updatedSender.balance == new BigDecimal("900.00")
        updatedReceiver.balance == new BigDecimal("100.00")
        updatedPayment.status == PaymentStatus.COMPLETED
    }
}
