package com.orderflow.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PaymentRefundFailedEvent(
        EventMetadata metadata,
        UUID paymentId,
        UUID refundId,
        UUID orderId,
        UUID customerId,
        BigDecimal requestedAmount,
        String currency,
        String failureCode,
        boolean retryable,
        Instant failedAt) {

    public static final int VERSION = 1;

    public PaymentRefundFailedEvent {
        PaymentRequestedEvent.validate(metadata, EventType.PAYMENT_REFUND_FAILED, orderId);
        Objects.requireNonNull(paymentId, "paymentId is required");
        Objects.requireNonNull(refundId, "refundId is required");
        Objects.requireNonNull(customerId, "customerId is required");
        Objects.requireNonNull(requestedAmount, "requestedAmount is required");
        if (requestedAmount.signum() <= 0) {
            throw new IllegalArgumentException("requestedAmount must be positive");
        }
        if (currency == null || !currency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("currency must be a three-letter uppercase code");
        }
        failureCode = PaymentSucceededEvent.requireText(failureCode, "failureCode");
        Objects.requireNonNull(failedAt, "failedAt is required");
    }
}
