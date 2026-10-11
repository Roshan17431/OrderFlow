package com.orderflow.order.client;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
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
        Objects.requireNonNull(userId, "userId is required");
        if (version < 0) throw new IllegalArgumentException("version cannot be negative");
        if (currency == null || !currency.matches("[A-Z]{3}")) throw new IllegalArgumentException("currency is invalid");
        items = List.copyOf(Objects.requireNonNull(items, "items are required"));
        if (distinctItemCount < 0 || totalQuantity < 0) throw new IllegalArgumentException("cart counts cannot be negative");
        Objects.requireNonNull(subtotal, "subtotal is required");
        if (subtotal.signum() < 0) throw new IllegalArgumentException("subtotal cannot be negative");
        Objects.requireNonNull(createdAt, "createdAt is required");
        Objects.requireNonNull(updatedAt, "updatedAt is required");
        Objects.requireNonNull(expiresAt, "expiresAt is required");
    }
}
