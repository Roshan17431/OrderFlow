package com.orderflow.events;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PaymentCancelledEvent(
        EventMetadata metadata,
        UUID paymentId,
        UUID orderId,
        UUID customerId,
        String provider,
        String cancellationReason,
        Instant cancelledAt) {

    public static final int VERSION = 1;

    public PaymentCancelledEvent {
        PaymentRequestedEvent.validate(metadata, EventType.PAYMENT_CANCELLED, orderId);
        Objects.requireNonNull(paymentId, "paymentId is required");
        Objects.requireNonNull(customerId, "customerId is required");
        provider = PaymentSucceededEvent.requireText(provider, "provider");
        cancellationReason = PaymentSucceededEvent.requireText(
                cancellationReason, "cancellationReason");
        Objects.requireNonNull(cancelledAt, "cancelledAt is required");
    }
}
