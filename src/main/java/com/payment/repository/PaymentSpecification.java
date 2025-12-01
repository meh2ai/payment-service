package com.payment.repository;

import com.payment.model.Payment;
import com.payment.model.PaymentStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.Nullable;

import java.util.UUID;

public final class PaymentSpecification {

    private PaymentSpecification() {
    }

    public static Specification<Payment> searchBy(
        @Nullable final UUID senderAccountId,
        @Nullable final PaymentStatus status) {

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
