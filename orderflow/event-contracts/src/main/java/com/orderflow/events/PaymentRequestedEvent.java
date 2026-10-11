package com.orderflow.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PaymentRequestedEvent(
        EventMetadata metadata,
        UUID orderId,
        UUID customerId,
        BigDecimal amount,
        String currency,
        Instant paymentDeadline,
        UUID inventoryReservationId,
        Instant inventoryReservationExpiresAt) {

    public static final int VERSION = 1;

    public PaymentRequestedEvent {
        validate(metadata, EventType.PAYMENT_REQUESTED, orderId);
        Objects.requireNonNull(customerId, "customerId is required");
        Objects.requireNonNull(amount, "amount is required");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        if (currency == null || !currency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("currency must be a three-letter uppercase code");
        }
        Objects.requireNonNull(paymentDeadline, "paymentDeadline is required");
        Objects.requireNonNull(inventoryReservationId, "inventoryReservationId is required");
        Objects.requireNonNull(
                inventoryReservationExpiresAt, "inventoryReservationExpiresAt is required");
        if (paymentDeadline.isAfter(inventoryReservationExpiresAt)) {
            throw new IllegalArgumentException(
                    "paymentDeadline cannot exceed inventory reservation expiry");
        }
    }

    static void validate(EventMetadata metadata, EventType type, UUID orderId) {
        Objects.requireNonNull(metadata, "metadata is required");
        metadata.require(type, VERSION);
        Objects.requireNonNull(orderId, "orderId is required");
        if (!"ORDER".equals(metadata.aggregateType())
                || !orderId.equals(metadata.aggregateId())) {
            throw new IllegalArgumentException("event must target the order aggregate");
        }
    }
}
