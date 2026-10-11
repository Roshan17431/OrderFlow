package com.orderflow.order.client;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record CartItemSnapshot(
        UUID productId,
        String productNameSnapshot,
        int quantity,
        BigDecimal unitPriceSnapshot,
        String currency,
        Long productVersionSnapshot,
        boolean purchasable,
        boolean priceChanged,
        Instant addedAt,
        Instant updatedAt) {
    public CartItemSnapshot {
        Objects.requireNonNull(productId, "productId is required");
        if (productNameSnapshot == null || productNameSnapshot.isBlank()) {
            throw new IllegalArgumentException("productNameSnapshot is required");
        }
        if (quantity <= 0) throw new IllegalArgumentException("quantity must be positive");
        Objects.requireNonNull(unitPriceSnapshot, "unitPriceSnapshot is required");
        if (unitPriceSnapshot.signum() < 0) throw new IllegalArgumentException("unitPriceSnapshot cannot be negative");
        if (currency == null || !currency.matches("[A-Z]{3}")) throw new IllegalArgumentException("currency is invalid");
        if (productVersionSnapshot == null || productVersionSnapshot < 0) throw new IllegalArgumentException("productVersionSnapshot is invalid");
        Objects.requireNonNull(addedAt, "addedAt is required");
        Objects.requireNonNull(updatedAt, "updatedAt is required");
    }
}
