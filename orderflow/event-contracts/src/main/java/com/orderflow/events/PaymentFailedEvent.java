package com.orderflow.events;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PaymentFailedEvent(
        EventMetadata metadata,
        UUID paymentId,
        UUID orderId,
        UUID customerId,
        String provider,
        String failureCode,
        String failureCategory,
        boolean retryable,
        Instant failedAt) {

    public static final int VERSION = 1;

    public PaymentFailedEvent {
        PaymentRequestedEvent.validate(metadata, EventType.PAYMENT_FAILED, orderId);
        Objects.requireNonNull(paymentId, "paymentId is required");
        Objects.requireNonNull(customerId, "customerId is required");
        provider = PaymentSucceededEvent.requireText(provider, "provider");
        failureCode = PaymentSucceededEvent.requireText(failureCode, "failureCode");
        failureCategory = PaymentSucceededEvent.requireText(
                failureCategory, "failureCategory");
        Objects.requireNonNull(failedAt, "failedAt is required");
    }
}
