package com.payment.unit.model

import com.payment.exception.ErrorCode
import com.payment.exception.PaymentException
import com.payment.model.Account
import spock.lang.Specification

class AccountSpec extends Specification {

    def "should create account with initial values"() {
        given:
        def id = UUID.randomUUID()
        def balance = new BigDecimal("1000.00")
        def currency = "EUR"

        when:
        def account = new Account(id, balance, currency)

        then:
        account.id == id
        account.balance == balance
        account.currency == currency
    }

    def "should debit account when sufficient balance"() {
        given:
        def account = new Account(UUID.randomUUID(), new BigDecimal("100.00"), "EUR")

        when:
        account.debit(new BigDecimal("30.00"))

        then:
        account.balance == new BigDecimal("70.00")
    }

    def "should debit exact balance to zero"() {
        given:
        def account = new Account(UUID.randomUUID(), new BigDecimal("100.00"), "EUR")

        when:
        account.debit(new BigDecimal("100.00"))

        then:
        account.balance == BigDecimal.ZERO
    }

    def "should throw PaymentException when insufficient balance"() {
        given:
        def accountId = UUID.randomUUID()
        def account = new Account(accountId, new BigDecimal("50.00"), "EUR")

        when:
        account.debit(new BigDecimal("100.00"))

        then:
        def ex = thrown(PaymentException)
        ex.errorCode == ErrorCode.INSUFFICIENT_BALANCE
        ex.message.contains("50.00")
        ex.message.contains("100.00")
    }

    def "should credit account"() {
        given:
        def account = new Account(UUID.randomUUID(), new BigDecimal("100.00"), "EUR")

        when:
        account.credit(new BigDecimal("50.00"))

        then:
        account.balance == new BigDecimal("150.00")
    }

    def "should credit zero balance account"() {
        given:
        def account = new Account(UUID.randomUUID(), BigDecimal.ZERO, "EUR")

        when:
        account.credit(new BigDecimal("100.00"))

        then:
        account.balance == new BigDecimal("100.00")
    }

    def "should handle multiple debit operations"() {
        given:
        def account = new Account(UUID.randomUUID(), new BigDecimal("100.00"), "EUR")

        when:
        account.debit(new BigDecimal("30.00"))
        account.debit(new BigDecimal("20.00"))
        account.debit(new BigDecimal("10.00"))

        then:
        account.balance == new BigDecimal("40.00")
    }

    def "should handle mixed debit and credit operations"() {
        given:
        def account = new Account(UUID.randomUUID(), new BigDecimal("100.00"), "EUR")

        when:
        account.debit(new BigDecimal("30.00"))
        account.credit(new BigDecimal("50.00"))
        account.debit(new BigDecimal("20.00"))

        then:
        account.balance == new BigDecimal("100.00")
    }
}
