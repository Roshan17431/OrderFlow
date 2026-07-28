package com.orderflow.events;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record OrderCreatedEvent(
        EventMetadata metadata,
        UUID orderId,
        UUID customerId,
        String currency,
        BigDecimal totalAmount,
        List<OrderCreatedItem> items) {

    public static final int VERSION = 1;

    public OrderCreatedEvent {
        Objects.requireNonNull(metadata, "metadata is required");
        metadata.require(EventType.ORDER_CREATED, VERSION);
        Objects.requireNonNull(orderId, "orderId is required");
        Objects.requireNonNull(customerId, "customerId is required");
        requireOrderAggregate(metadata, orderId);
        if (currency == null || !currency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("currency must be a three-letter uppercase code");
        }
        Objects.requireNonNull(totalAmount, "totalAmount is required");
        if (totalAmount.signum() < 0) {
            throw new IllegalArgumentException("totalAmount cannot be negative");
        }
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
