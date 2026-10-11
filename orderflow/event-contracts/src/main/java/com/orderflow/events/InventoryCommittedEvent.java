package com.orderflow.events;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record InventoryCommittedEvent(
        EventMetadata metadata, UUID orderId, UUID reservationId, Instant committedAt) {
    public static final int VERSION = 1;

    public InventoryCommittedEvent {
        Objects.requireNonNull(metadata);
        metadata.require(EventType.INVENTORY_COMMITTED, VERSION);
        Objects.requireNonNull(orderId);
        Objects.requireNonNull(reservationId);
        Objects.requireNonNull(committedAt);
        if (!"ORDER".equals(metadata.aggregateType()) || !orderId.equals(metadata.aggregateId())) {
            throw new IllegalArgumentException("Event must target the order aggregate");
        }
    }
}
