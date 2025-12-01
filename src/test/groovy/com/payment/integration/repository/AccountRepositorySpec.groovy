package com.payment.integration.repository

import com.payment.integration.IntegrationTestBase
import com.payment.model.Account
import com.payment.repository.AccountRepository
import org.springframework.beans.factory.annotation.Autowired

import java.time.Instant

class AccountRepositorySpec extends IntegrationTestBase {

    @Autowired
    AccountRepository accountRepository

    def cleanup() {
        accountRepository.deleteAll()
    }

    def "should save and retrieve account"() {
        given:
        def accountId = UUID.randomUUID()
        def account = new Account(accountId, new BigDecimal("1000.00"), "EUR")

        when:
        accountRepository.save(account)
        def retrieved = accountRepository.findById(accountId)

        then:
        retrieved.isPresent()
        retrieved.get().id == accountId
        retrieved.get().balance == new BigDecimal("1000.00")
        retrieved.get().currency == "EUR"
        retrieved.get().version == 0L
        retrieved.get().createdAt != null
        retrieved.get().updatedAt != null
    }

    def "should update account balance with optimistic locking"() {
        given:
        def accountId = UUID.randomUUID()
        def account = new Account(accountId, new BigDecimal("500.00"), "EUR")
        accountRepository.save(account)

        when:
        def loaded = accountRepository.findById(accountId).get()
        loaded.debit(new BigDecimal("100.00"))
        accountRepository.save(loaded)

        then:
        def updated = accountRepository.findById(accountId).get()
        updated.balance == new BigDecimal("400.00")
        updated.version == 1L
    }

    def "should return empty optional for non-existent account"() {
        when:
        def result = accountRepository.findById(UUID.randomUUID())

        then:
        !result.isPresent()
    }

    def "should detect concurrent modifications with optimistic locking"() {
        given:
        def accountId = UUID.randomUUID()
        def account = new Account(accountId, new BigDecimal("1000.00"), "EUR")
        accountRepository.saveAndFlush(account)

        when:
        def account1 = accountRepository.findById(accountId).get()
        def account2 = accountRepository.findById(accountId).get()

        account1.debit(new BigDecimal("100.00"))
        accountRepository.saveAndFlush(account1)

        account2.debit(new BigDecimal("50.00"))
        accountRepository.saveAndFlush(account2)

        then:
        thrown(Exception)
    }

    def "should set audit timestamps on create"() {
        given:
        def accountId = UUID.randomUUID()
        def account = new Account(accountId, new BigDecimal("100.00"), "EUR")

        when:
        def before = Instant.now()
        accountRepository.save(account)
        def after = Instant.now()
        def saved = accountRepository.findById(accountId).get()

        then:
        saved.createdAt != null
        saved.updatedAt != null
        !saved.createdAt.isBefore(before.minusSeconds(1))
        !saved.createdAt.isAfter(after.plusSeconds(1))
    }
}
