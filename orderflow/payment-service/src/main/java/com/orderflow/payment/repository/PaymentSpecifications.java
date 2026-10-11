package com.orderflow.payment.repository;

import com.orderflow.payment.domain.Payment;
import com.orderflow.payment.domain.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

public final class PaymentSpecifications {
    private PaymentSpecifications() {}

    public static Specification<Payment> filters(
            UUID orderId, UUID customerId, PaymentStatus status, String provider,
            String currency, Instant createdFrom, Instant createdTo,
            BigDecimal minAmount, BigDecimal maxAmount) {
        return Specification.allOf(
                equal("orderId", orderId),
                equal("customerId", customerId),
                equal("status", status),
                equal("provider", normalize(provider)),
                equal("currency", normalize(currency)),
                greaterOrEqual("createdAt", createdFrom),
                lessOrEqual("createdAt", createdTo),
                greaterOrEqual("amount", minAmount),
                lessOrEqual("amount", maxAmount));
    }

    private static <T> Specification<Payment> equal(String field, T value) {
        return value == null ? null : (root, query, cb) -> cb.equal(root.get(field), value);
    }

    private static <T extends Comparable<? super T>> Specification<Payment> greaterOrEqual(
            String field, T value) {
        return value == null
                ? null
                : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get(field), value);
    }

    private static <T extends Comparable<? super T>> Specification<Payment> lessOrEqual(
            String field, T value) {
        return value == null
                ? null
                : (root, query, cb) -> cb.lessThanOrEqualTo(root.get(field), value);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.toUpperCase();
    }
}
