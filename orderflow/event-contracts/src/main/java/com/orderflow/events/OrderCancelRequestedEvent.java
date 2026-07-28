package com.orderflow.events;

import java.util.Objects;
import java.util.UUID;

public record OrderCancelRequestedEvent(
        EventMetadata metadata, UUID orderId, UUID customerId, String reason) {

    public static final int VERSION = 1;

    public OrderCancelRequestedEvent {
        Objects.requireNonNull(metadata, "metadata is required");
        metadata.require(EventType.ORDER_CANCEL_REQUESTED, VERSION);
        Objects.requireNonNull(orderId, "orderId is required");
        Objects.requireNonNull(customerId, "customerId is required");
        if (!"ORDER".equals(metadata.aggregateType())
                || !orderId.equals(metadata.aggregateId())) {
            throw new IllegalArgumentException("event must target the order aggregate");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason is required");
        }
    }
}
