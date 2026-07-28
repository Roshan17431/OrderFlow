package com.orderflow.events;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record InventoryReservedEvent(
        EventMetadata metadata,
        UUID orderId,
        UUID reservationId,
        Instant expiresAt,
        List<InventoryReservedItem> items) {

    public static final int VERSION = 1;

    public InventoryReservedEvent {
        Objects.requireNonNull(metadata, "metadata is required");
        metadata.require(EventType.INVENTORY_RESERVED, VERSION);
        Objects.requireNonNull(orderId, "orderId is required");
        requireOrderAggregate(metadata, orderId);
        Objects.requireNonNull(reservationId, "reservationId is required");
        Objects.requireNonNull(expiresAt, "expiresAt is required");
        items = List.copyOf(Objects.requireNonNull(items, "items are required"));
        if (items.isEmpty()) {
            throw new IllegalArgumentException("items cannot be empty");
        }
    }

    private static void requireOrderAggregate(EventMetadata metadata, UUID orderId) {
        if (!"ORDER".equals(metadata.aggregateType())
                || !orderId.equals(metadata.aggregateId())) {
            throw new IllegalArgumentException("event must target the order aggregate");
        }
    }
}
