package com.payment.unit.repository

import com.payment.model.Payment
import com.payment.model.PaymentStatus
import com.payment.repository.PaymentSpecification
import jakarta.persistence.criteria.*
import spock.lang.Specification

class PaymentSpecificationSpec extends Specification {

    Root<Payment> root = Mock()
    CriteriaQuery<?> query = Mock()
    CriteriaBuilder cb = Mock()

    Path senderPath = Mock()
    Path statusPath = Mock()
    Predicate conjunction = Mock()
    Predicate senderPredicate = Mock()
    Predicate statusPredicate = Mock()

    def setup() {
        root.get("senderAccountId") >> senderPath
        root.get("status") >> statusPath
        cb.conjunction() >> conjunction
    }

    def "should return conjunction when no filters provided"() {
        when:
        def spec = PaymentSpecification.searchBy(null, null)
        def result = spec.toPredicate(root, query, cb)

        then:
        result == conjunction
        0 * cb.and(_, _)
    }

    def "should filter by senderAccountId"() {
        given:
        def senderId = UUID.randomUUID()
        cb.equal(senderPath, senderId) >> senderPredicate
        cb.and(conjunction, senderPredicate) >> senderPredicate

        when:
        def spec = PaymentSpecification.searchBy(senderId, null)
        def result = spec.toPredicate(root, query, cb)

        then:
        result == senderPredicate
    }

    def "should filter by status"() {
        given:
        cb.equal(statusPath, PaymentStatus.COMPLETED) >> statusPredicate
        cb.and(conjunction, statusPredicate) >> statusPredicate

        when:
        def spec = PaymentSpecification.searchBy(null, PaymentStatus.COMPLETED)
        def result = spec.toPredicate(root, query, cb)

        then:
        result == statusPredicate
    }

    def "should filter by both senderAccountId and status"() {
        given:
        def senderId = UUID.randomUUID()
        def combinedPredicate = Mock(Predicate)

        cb.equal(senderPath, senderId) >> senderPredicate
        cb.and(conjunction, senderPredicate) >> senderPredicate
        cb.equal(statusPath, PaymentStatus.PENDING) >> statusPredicate
        cb.and(senderPredicate, statusPredicate) >> combinedPredicate

        when:
        def spec = PaymentSpecification.searchBy(senderId, PaymentStatus.PENDING)
        def result = spec.toPredicate(root, query, cb)

        then:
        result == combinedPredicate
    }
}
