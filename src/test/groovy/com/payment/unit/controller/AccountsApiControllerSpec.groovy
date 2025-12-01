package com.payment.unit.controller

import com.payment.api.model.AccountRequest
import com.payment.api.model.AccountResponse
import com.payment.controller.AccountsApiController
import com.payment.exception.ErrorCode
import com.payment.exception.PaymentException
import com.payment.service.AccountService
import org.springframework.http.HttpStatus
import spock.lang.Specification
import spock.lang.Subject

class AccountsApiControllerSpec extends Specification {

    AccountService accountService = Mock()

    @Subject
    AccountsApiController controller = new AccountsApiController(accountService)

    def "should create account and return 201 Created"() {
        given:
        def accountId = UUID.randomUUID()
        def request = new AccountRequest()
        request.setAccountId(accountId)
        request.setBalance("1000.00")
        request.setCurrency("EUR")

        def expectedResponse = new AccountResponse()
        expectedResponse.setAccountId(accountId)
        expectedResponse.setBalance("1000.00")
        expectedResponse.setCurrency("EUR")

        when:
        def response = controller.createAccount(request)

        then:
        1 * accountService.createAccount(request) >> expectedResponse

        and:
        response.statusCode == HttpStatus.CREATED
        response.body.accountId == accountId
        response.body.balance == "1000.00"
        response.body.currency == "EUR"
    }

    def "should get account balance"() {
        given:
        def accountId = UUID.randomUUID()
        def expectedResponse = new AccountResponse()
        expectedResponse.setAccountId(accountId)
        expectedResponse.setBalance("1500.00")
        expectedResponse.setCurrency("EUR")

        when:
        def response = controller.getAccount(accountId)

        then:
        1 * accountService.getAccount(accountId) >> expectedResponse

        and:
        response.statusCode == HttpStatus.OK
        response.body.accountId == accountId
        response.body.balance == "1500.00"
        response.body.currency == "EUR"
    }

    def "should propagate PaymentException"() {
        given:
        def accountId = UUID.randomUUID()

        when:
        controller.getAccount(accountId)

        then:
        1 * accountService.getAccount(accountId) >> {
            throw PaymentException.accountNotFound(accountId)
        }

        and:
        def ex = thrown(PaymentException)
        ex.errorCode == ErrorCode.ACCOUNT_NOT_FOUND
    }
}
