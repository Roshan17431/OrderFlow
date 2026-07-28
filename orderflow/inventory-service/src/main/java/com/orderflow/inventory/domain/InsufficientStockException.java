package com.orderflow.inventory.domain;

import java.util.UUID;

public final class InsufficientStockException extends IllegalStateException {

    private final UUID productId;
    private final long requestedQuantity;
    private final long availableQuantity;

    public InsufficientStockException(
            UUID productId, long requestedQuantity, long availableQuantity) {
        super("Insufficient inventory");
        this.productId = productId;
        this.requestedQuantity = requestedQuantity;
        this.availableQuantity = availableQuantity;
    }

    public UUID productId() {
        return productId;
    }

    public long requestedQuantity() {
        return requestedQuantity;
    }

    public long availableQuantity() {
        return availableQuantity;
    }
}
