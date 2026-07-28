package com.orderflow.events;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record InventoryReleasedEvent(
        EventMetadata metadata,
        UUID orderId,
        UUID reservationId,
        Instant releasedAt,
        String reason,
        boolean stockReleased) {

    public static final int VERSION = 1;

    public InventoryReleasedEvent {
        Objects.requireNonNull(metadata, "metadata is required");
        metadata.require(EventType.INVENTORY_RELEASED, VERSION);
        Objects.requireNonNull(orderId, "orderId is required");
        if (!"ORDER".equals(metadata.aggregateType())
                || !orderId.equals(metadata.aggregateId())) {
            throw new IllegalArgumentException("event must target the order aggregate");
        }
        Objects.requireNonNull(reservationId, "reservationId is required");
        Objects.requireNonNull(releasedAt, "releasedAt is required");
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason is required");
        }
    }
}
