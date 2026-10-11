package com.orderflow.events;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PaymentCancelRequestedEvent(
        EventMetadata metadata,
        UUID orderId,
        UUID customerId,
        UUID paymentId,
        String reasonCode,
        Instant requestedAt) {

    public static final int VERSION = 1;

    public PaymentCancelRequestedEvent {
        PaymentRequestedEvent.validate(metadata, EventType.PAYMENT_CANCEL_REQUESTED, orderId);
        Objects.requireNonNull(customerId, "customerId is required");
        if (reasonCode == null || reasonCode.isBlank()) {
            throw new IllegalArgumentException("reasonCode is required");
        }
        Objects.requireNonNull(requestedAt, "requestedAt is required");
    }
}
