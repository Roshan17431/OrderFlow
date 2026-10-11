package com.orderflow.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PaymentRefundedEvent(
        EventMetadata metadata,
        UUID paymentId,
        UUID refundId,
        UUID orderId,
        UUID customerId,
        BigDecimal refundedAmount,
        BigDecimal cumulativeRefundedAmount,
        BigDecimal originalPaymentAmount,
        String currency,
        boolean fullRefund,
        Instant refundedAt) {

    public static final int VERSION = 1;

    public PaymentRefundedEvent {
        PaymentRequestedEvent.validate(metadata, EventType.PAYMENT_REFUNDED, orderId);
        Objects.requireNonNull(paymentId, "paymentId is required");
        Objects.requireNonNull(refundId, "refundId is required");
        Objects.requireNonNull(customerId, "customerId is required");
        Objects.requireNonNull(refundedAmount, "refundedAmount is required");
        Objects.requireNonNull(cumulativeRefundedAmount, "cumulativeRefundedAmount is required");
        Objects.requireNonNull(originalPaymentAmount, "originalPaymentAmount is required");
        if (refundedAmount.signum() <= 0
                || cumulativeRefundedAmount.signum() <= 0
                || originalPaymentAmount.signum() <= 0
                || cumulativeRefundedAmount.compareTo(originalPaymentAmount) > 0) {
            throw new IllegalArgumentException("refund amounts are invalid");
        }
        if (currency == null || !currency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("currency must be a three-letter uppercase code");
        }
        if (fullRefund != (cumulativeRefundedAmount.compareTo(originalPaymentAmount) == 0)) {
            throw new IllegalArgumentException("fullRefund does not match cumulative amount");
        }
        Objects.requireNonNull(refundedAt, "refundedAt is required");
    }
}
