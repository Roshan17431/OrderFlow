package com.orderflow.order.client;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CartSnapshot(
        UUID userId,
        long version,
        String currency,
        List<CartItemSnapshot> items,
        int distinctItemCount,
        int totalQuantity,
        BigDecimal subtotal,
        Instant createdAt,
        Instant updatedAt,
        Instant expiresAt) {
    public CartSnapshot {
        items = List.copyOf(items);
    }
}
