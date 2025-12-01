package com.payment.unit.service

import com.payment.api.model.AccountRequest
import com.payment.exception.ErrorCode
import com.payment.exception.PaymentException
import com.payment.model.Account
import com.payment.repository.AccountRepository
import com.payment.service.AccountService
import org.modelmapper.ModelMapper
import spock.lang.Specification
import spock.lang.Subject

class AccountServiceSpec extends Specification {

    AccountRepository accountRepository = Mock()
    ModelMapper modelMapper = new ModelMapper()

    @Subject
    AccountService accountService = new AccountService(accountRepository, modelMapper)

    def "should create account with provided id"() {
        given:
        def accountId = UUID.randomUUID()
        def request = new AccountRequest()
        request.setAccountId(accountId)
        request.setBalance("1000.00")
        request.setCurrency("EUR")

        when:
        def response = accountService.createAccount(request)

        then:
        1 * accountRepository.save({ Account a ->
            a.id == accountId &&
                    a.balance == new BigDecimal("1000.00") &&
                    a.currency == "EUR"
        })

        and:
        response.accountId == accountId
        response.balance == "1000.00"
        response.currency == "EUR"
    }

    def "should create account with generated id when not provided"() {
        given:
        def request = new AccountRequest()
        request.setBalance("500.00")
        request.setCurrency("USD")

        when:
        def response = accountService.createAccount(request)

        then:
        1 * accountRepository.save({ Account a ->
            a.id != null &&
                    a.balance == new BigDecimal("500.00") &&
                    a.currency == "USD"
        })

        and:
        response.accountId != null
        response.balance == "500.00"
        response.currency == "USD"
    }

    def "should return balance for existing account"() {
        given:
        def accountId = UUID.randomUUID()
        def account = new Account(accountId, new BigDecimal("1500.50"), "EUR")

        when:
        def response = accountService.getAccount(accountId)

        then:
        1 * accountRepository.findById(accountId) >> Optional.of(account)

        and:
        response.accountId == accountId
        response.balance == "1500.50"
        response.currency == "EUR"
    }

    def "should throw PaymentException for non-existent account"() {
        given:
        def accountId = UUID.randomUUID()

        when:
        accountService.getAccount(accountId)

        then:
        1 * accountRepository.findById(accountId) >> Optional.empty()

        and:
        def ex = thrown(PaymentException)
        ex.errorCode == ErrorCode.ACCOUNT_NOT_FOUND
    }
}
