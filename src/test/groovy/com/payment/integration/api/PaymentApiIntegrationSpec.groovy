package com.payment.integration.api

import com.payment.api.model.PaymentRequest
import com.payment.integration.IntegrationTestBase
import com.payment.model.Account
import com.payment.model.PaymentStatus
import com.payment.repository.AccountRepository
import com.payment.repository.PaymentRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import spock.util.concurrent.PollingConditions

class PaymentApiIntegrationSpec extends IntegrationTestBase {

    @Autowired
    TestRestTemplate restTemplate

    @Autowired
    AccountRepository accountRepository

    @Autowired
    PaymentRepository paymentRepository

    def conditions = new PollingConditions(timeout: 10, initialDelay: 0.5, factor: 1.25)

    def senderAccountId = UUID.randomUUID()
    def receiverAccountId = UUID.randomUUID()

    def setup() {
        accountRepository.save(new Account(senderAccountId, new BigDecimal("1000.00"), "EUR"))
        accountRepository.save(new Account(receiverAccountId, new BigDecimal("500.00"), "EUR"))
    }

    def cleanup() {
        paymentRepository.deleteAll()
        accountRepository.deleteAll()
    }

    def "should submit payment and process asynchronously"() {
        given:
        def headers = new HttpHeaders()
        headers.setContentType(MediaType.APPLICATION_JSON)
        headers.set("Idempotency-Key", UUID.randomUUID().toString())
        def paymentRequest = new PaymentRequest(senderAccountId, receiverAccountId, "100.00", "EUR")

        when:
        def response = restTemplate.postForEntity(
                "/api/v1/payments",
                new HttpEntity<>(paymentRequest, headers),
                Map)

        then:
        response.statusCode == HttpStatus.ACCEPTED
        response.body.paymentId != null
        response.body.status == "PENDING"

        and: "payment is eventually completed"
        conditions.eventually {
            def payment = paymentRepository.findById(UUID.fromString(response.body.paymentId as String))
            assert payment.isPresent()
            assert payment.get().status == PaymentStatus.COMPLETED
        }

        and: "balances are updated"
        conditions.eventually {
            def sender = accountRepository.findById(senderAccountId)
            def receiver = accountRepository.findById(receiverAccountId)
            assert sender.get().balance == new BigDecimal("900.00")
            assert receiver.get().balance == new BigDecimal("600.00")
        }
    }

    def "should return same payment for duplicate idempotency key"() {
        given:
        def idempotencyKey = UUID.randomUUID().toString()
        def headers = new HttpHeaders()
        headers.setContentType(MediaType.APPLICATION_JSON)
        headers.set("Idempotency-Key", idempotencyKey)
        def paymentRequest = new PaymentRequest(senderAccountId, receiverAccountId, "50.00", "EUR")

        when:
        def response1 = restTemplate.postForEntity(
                "/api/v1/payments",
                new HttpEntity<>(paymentRequest, headers),
                Map)
        def response2 = restTemplate.postForEntity(
                "/api/v1/payments",
                new HttpEntity<>(paymentRequest, headers),
                Map)

        then:
        response1.statusCode == HttpStatus.ACCEPTED
        response2.statusCode == HttpStatus.ACCEPTED
        response1.body.paymentId == response2.body.paymentId

        and: "only one payment exists"
        paymentRepository.count() == 1
    }

    def "should fail payment when insufficient balance"() {
        given:
        def headers = new HttpHeaders()
        headers.setContentType(MediaType.APPLICATION_JSON)
        headers.set("Idempotency-Key", UUID.randomUUID().toString())
        def paymentRequest = new PaymentRequest(senderAccountId, receiverAccountId, "5000.00", "EUR")

        when:
        def response = restTemplate.postForEntity(
                "/api/v1/payments",
                new HttpEntity<>(paymentRequest, headers),
                Map)

        then:
        response.statusCode == HttpStatus.ACCEPTED

        and: "payment is eventually failed"
        conditions.eventually {
            def payment = paymentRepository.findById(UUID.fromString(response.body.paymentId as String))
            assert payment.isPresent()
            assert payment.get().status == PaymentStatus.FAILED
            assert payment.get().errorCode.name().contains("INSUFFICIENT")
        }
    }

    def "should get payment by id"() {
        given:
        def headers = new HttpHeaders()
        headers.setContentType(MediaType.APPLICATION_JSON)
        headers.set("Idempotency-Key", UUID.randomUUID().toString())
        def paymentRequest = new PaymentRequest(senderAccountId, receiverAccountId, "25.00", "EUR")

        and:
        def createResponse = restTemplate.postForEntity(
                "/api/v1/payments",
                new HttpEntity<>(paymentRequest, headers),
                Map)
        def paymentId = createResponse.body.paymentId

        when:
        def response = restTemplate.getForEntity("/api/v1/payments/${paymentId}", Map)

        then:
        response.statusCode == HttpStatus.OK
        response.body.paymentId == paymentId
        response.body.amount == "25.00"
        response.body.currency == "EUR"
    }

    def "should return 404 for non-existent payment"() {
        when:
        def response = restTemplate.getForEntity(
                "/api/v1/payments/${UUID.randomUUID()}", Map)

        then:
        response.statusCode == HttpStatus.NOT_FOUND
        response.body.errorCode == "PAYMENT_NOT_FOUND"
        response.body.numericCode == 1001
    }

    def "should list payments with pagination"() {
        given:
        def headers = new HttpHeaders()
        headers.setContentType(MediaType.APPLICATION_JSON)

        (1..5).each { i ->
            headers.set("Idempotency-Key", UUID.randomUUID().toString())
            restTemplate.postForEntity(
                    "/api/v1/payments",
                    new HttpEntity<>(new PaymentRequest(senderAccountId, receiverAccountId, "${i}.00", "EUR"), headers),
                    Map
            )
        }

        when:
        def response = restTemplate.getForEntity(
                "/api/v1/payments?page=0&size=3", Map)

        then:
        response.statusCode == HttpStatus.OK
        response.body.content.size() == 3
        response.body.totalElements == 5
        response.body.totalPages == 2
    }

    def "should filter payments by sender"() {
        given:
        def headers = new HttpHeaders()
        headers.setContentType(MediaType.APPLICATION_JSON)
        headers.set("Idempotency-Key", UUID.randomUUID().toString())

        and:
        restTemplate.postForEntity(
                "/api/v1/payments",
                new HttpEntity<>(new PaymentRequest(senderAccountId, receiverAccountId, "10.00", "EUR"), headers),
                Map
        )

        when:
        def response = restTemplate.getForEntity(
                "/api/v1/payments?senderAccountId=${senderAccountId}", Map)

        then:
        response.statusCode == HttpStatus.OK
        response.body.content.size() >= 1
        response.body.content.every { it.senderAccountId == senderAccountId.toString() }
    }
}
