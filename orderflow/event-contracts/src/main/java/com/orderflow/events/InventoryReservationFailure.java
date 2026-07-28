package com.orderflow.events;

import java.util.Objects;
import java.util.UUID;

public record InventoryReservationFailure(
        UUID productId, long requestedQuantity, long availableQuantity, String reasonCode) {
    public InventoryReservationFailure {
        Objects.requireNonNull(productId, "productId is required");
        if (requestedQuantity <= 0 || availableQuantity < 0) {
            throw new IllegalArgumentException("quantities are invalid");
        }
        if (reasonCode == null || reasonCode.isBlank()) {
            throw new IllegalArgumentException("reasonCode is required");
        }
    }
}
