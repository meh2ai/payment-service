package com.payment.repository;

import com.payment.model.Payment;
import com.payment.model.PaymentStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

public final class PaymentSpecification {

    private PaymentSpecification() {
    }

    public static Specification<Payment> searchBy(UUID senderAccountId, PaymentStatus status) {

        return (root, query, cb) -> {
            Predicate predicate = cb.conjunction();

            if (senderAccountId != null) {
                predicate = cb.and(predicate, cb.equal(root.get("senderAccountId"), senderAccountId));
            }

            if (status != null) {
                predicate = cb.and(predicate, cb.equal(root.get("status"), status));
            }

            return predicate;
        };
    }
}
