package com.payment.unit.controller

import com.payment.api.model.PaymentAcceptedResponse
import com.payment.api.model.PaymentListResponse
import com.payment.api.model.PaymentRequest
import com.payment.api.model.PaymentResponse
import com.payment.api.model.PaymentStatus
import com.payment.controller.PaymentsApiController
import com.payment.service.PaymentService
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import spock.lang.Specification
import spock.lang.Subject

class PaymentsApiControllerSpec extends Specification {

    PaymentService paymentService = Mock()

    @Subject
    PaymentsApiController controller = new PaymentsApiController(paymentService)

    def "should submit payment and return 202 Accepted"() {
        given:
        def request = new PaymentRequest()
        request.setSenderAccountId(UUID.randomUUID())
        request.setReceiverAccountId(UUID.randomUUID())
        request.setAmount("100.00")
        request.setCurrency("EUR")

        def idempotencyKey = "test-key-123"
        def paymentId = UUID.randomUUID()

        def expectedResponse = new PaymentAcceptedResponse()
        expectedResponse.setPaymentId(paymentId)
        expectedResponse.setStatus(PaymentStatus.PENDING)

        when:
        def response = controller.submitPayment(idempotencyKey, request)

        then:
        1 * paymentService.submitPayment(request, idempotencyKey) >> expectedResponse

        and:
        response.statusCode == HttpStatus.ACCEPTED
        response.body.paymentId == paymentId
        response.body.status == PaymentStatus.PENDING
    }

    def "should get payment by id"() {
        given:
        def paymentId = UUID.randomUUID()
        def expectedResponse = new PaymentResponse()
        expectedResponse.setPaymentId(paymentId)
        expectedResponse.setAmount("50.00")
        expectedResponse.setCurrency("EUR")
        expectedResponse.setStatus(PaymentStatus.COMPLETED)

        when:
        def response = controller.getPayment(paymentId)

        then:
        1 * paymentService.getPayment(paymentId) >> expectedResponse

        and:
        response.statusCode == HttpStatus.OK
        response.body.paymentId == paymentId
        response.body.status == PaymentStatus.COMPLETED
    }

    def "should list payments with pageable"() {
        given:
        def pageable = PageRequest.of(0, 20, Sort.by("createdAt"))
        def expectedResponse = new PaymentListResponse()
        expectedResponse.setContent([])
        expectedResponse.setPage(0)
        expectedResponse.setSize(20)
        expectedResponse.setTotalElements(0)
        expectedResponse.setTotalPages(0)

        when:
        def response = controller.listPayments(null, null, pageable)

        then:
        1 * paymentService.listPayments(null, null, pageable) >> expectedResponse

        and:
        response.statusCode == HttpStatus.OK
    }

    def "should list payments with custom pageable"() {
        given:
        def pageable = PageRequest.of(2, 50, Sort.by(Sort.Direction.DESC, "amount"))
        def expectedResponse = new PaymentListResponse()
        expectedResponse.setContent([])
        expectedResponse.setPage(2)
        expectedResponse.setSize(50)
        expectedResponse.setTotalElements(0)
        expectedResponse.setTotalPages(0)

        when:
        def response = controller.listPayments(null, null, pageable)

        then:
        1 * paymentService.listPayments(null, null, pageable) >> expectedResponse

        and:
        response.statusCode == HttpStatus.OK
    }

    def "should list payments with filters and pageable"() {
        given:
        def senderAccountId = UUID.randomUUID()
        def status = PaymentStatus.PENDING
        def pageable = PageRequest.of(0, 20)

        def expectedResponse = new PaymentListResponse()
        expectedResponse.setContent([])
        expectedResponse.setPage(0)
        expectedResponse.setSize(20)
        expectedResponse.setTotalElements(0)
        expectedResponse.setTotalPages(0)

        when:
        def response = controller.listPayments(senderAccountId, status, pageable)

        then:
        1 * paymentService.listPayments(senderAccountId,
                com.payment.model.PaymentStatus.PENDING, pageable) >> expectedResponse

        and:
        response.statusCode == HttpStatus.OK
    }
}
