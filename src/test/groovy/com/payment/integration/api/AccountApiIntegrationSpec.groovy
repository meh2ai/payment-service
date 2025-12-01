package com.payment.integration.api

import com.payment.api.model.AccountRequest
import com.payment.integration.IntegrationTestBase
import com.payment.model.Account
import com.payment.repository.AccountRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType

class AccountApiIntegrationSpec extends IntegrationTestBase {

    @Autowired
    TestRestTemplate restTemplate

    @Autowired
    AccountRepository accountRepository

    def cleanup() {
        accountRepository.deleteAll()
    }

    def "should create account"() {
        given:
        def accountId = UUID.randomUUID()
        def headers = new HttpHeaders()
        headers.setContentType(MediaType.APPLICATION_JSON)
        def accountRequest = new AccountRequest("1000.00", "EUR").accountId(accountId)

        when:
        def response = restTemplate.postForEntity(
                "/api/v1/accounts",
                new HttpEntity<>(accountRequest, headers),
                Map)

        then:
        response.statusCode == HttpStatus.CREATED
        response.body.accountId == accountId.toString()
        response.body.balance == "1000.00"
        response.body.currency == "EUR"

        and:
        def saved = accountRepository.findById(accountId)
        saved.isPresent()
        saved.get().balance == new BigDecimal("1000.00")
    }

    def "should create account with generated id"() {
        given:
        def headers = new HttpHeaders()
        headers.setContentType(MediaType.APPLICATION_JSON)
        def accountRequest = new AccountRequest("500.00", "EUR")

        when:
        def response = restTemplate.postForEntity(
                "/api/v1/accounts",
                new HttpEntity<>(accountRequest, headers),
                Map)

        then:
        response.statusCode == HttpStatus.CREATED
        response.body.accountId != null
        response.body.balance == "500.00"
        response.body.currency == "EUR"
    }

    def "should get account balance"() {
        given:
        def accountId = UUID.randomUUID()
        accountRepository.save(new Account(accountId, new BigDecimal("1500.75"), "EUR"))

        when:
        def response = restTemplate.getForEntity("/api/v1/accounts/${accountId}", Map)

        then:
        response.statusCode == HttpStatus.OK
        response.body.accountId == accountId.toString()
        response.body.balance == "1500.75"
        response.body.currency == "EUR"
    }

    def "should return 404 for non-existent account"() {
        when:
        def response = restTemplate.getForEntity(
                "/api/v1/accounts/${UUID.randomUUID()}", Map)

        then:
        response.statusCode == HttpStatus.NOT_FOUND
        response.body.errorCode == "ACCOUNT_NOT_FOUND"
        response.body.numericCode == 2001
    }
}
