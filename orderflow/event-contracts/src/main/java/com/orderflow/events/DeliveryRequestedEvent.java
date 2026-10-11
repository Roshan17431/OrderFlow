package com.orderflow.events;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record DeliveryRequestedEvent(
        EventMetadata metadata,
        UUID orderId,
        UUID customerId,
        ShippingAddressSnapshot shippingAddress,
        Instant requestedAt) {
    public static final int VERSION = 1;
    public DeliveryRequestedEvent {
        validate(metadata, EventType.DELIVERY_REQUESTED, orderId);
        Objects.requireNonNull(customerId, "customerId is required");
        Objects.requireNonNull(shippingAddress, "shippingAddress is required");
        Objects.requireNonNull(requestedAt, "requestedAt is required");
    }
    static void validate(EventMetadata metadata, EventType type, UUID orderId) {
        Objects.requireNonNull(metadata, "metadata is required");
        Objects.requireNonNull(orderId, "orderId is required");
        metadata.require(type, VERSION);
        if (!orderId.equals(metadata.aggregateId())) throw new IllegalArgumentException("aggregateId must equal orderId");
    }
}
