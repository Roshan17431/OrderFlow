package com.orderflow.events;

import java.util.Objects;
import java.util.UUID;

public record InventoryReservedItem(UUID productId, long quantity) {
    public InventoryReservedItem {
        Objects.requireNonNull(productId, "productId is required");
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
    }
}
