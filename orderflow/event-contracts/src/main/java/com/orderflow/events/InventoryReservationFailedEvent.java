package com.orderflow.events;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record InventoryReservationFailedEvent(
        EventMetadata metadata,
        UUID orderId,
        List<InventoryReservationFailure> failures) {

    public static final int VERSION = 1;

    public InventoryReservationFailedEvent {
        Objects.requireNonNull(metadata, "metadata is required");
        metadata.require(EventType.INVENTORY_RESERVATION_FAILED, VERSION);
        Objects.requireNonNull(orderId, "orderId is required");
        if (!"ORDER".equals(metadata.aggregateType())
                || !orderId.equals(metadata.aggregateId())) {
            throw new IllegalArgumentException("event must target the order aggregate");
        }
        failures = List.copyOf(Objects.requireNonNull(failures, "failures are required"));
        if (failures.isEmpty()) {
            throw new IllegalArgumentException("failures cannot be empty");
        }
    }
}
