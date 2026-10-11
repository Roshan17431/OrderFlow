package com.orderflow.events;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record InventoryCommitRequestedEvent(
        EventMetadata metadata, UUID orderId, UUID reservationId, Instant paidAt) {
    public static final int VERSION = 1;

    public InventoryCommitRequestedEvent {
        Objects.requireNonNull(metadata);
        metadata.require(EventType.INVENTORY_COMMIT_REQUESTED, VERSION);
        Objects.requireNonNull(orderId);
        Objects.requireNonNull(reservationId);
        Objects.requireNonNull(paidAt);
        if (!"ORDER".equals(metadata.aggregateType()) || !orderId.equals(metadata.aggregateId())) {
            throw new IllegalArgumentException("Event must target the order aggregate");
        }
    }
}
