package com.orderflow.cart.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record Cart(
        UUID userId,
        long version,
        String currency,
        List<CartItem> items,
        Instant createdAt,
        Instant updatedAt,
        Instant expiresAt) {

    public Cart {
        items = items == null
                ? List.of()
                : items.stream()
                        .sorted(Comparator.comparing(CartItem::addedAt))
                        .toList();
    }

    public static Cart empty(UUID userId) {
        return new Cart(userId, 0, null, List.of(), null, null, null);
    }

    public int distinctItemCount() {
        return items.size();
    }

    public int totalQuantity() {
        return items.stream().mapToInt(CartItem::quantity).sum();
    }

    public BigDecimal subtotal() {
        return items.stream()
                .map(CartItem::lineTotal)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add)
                .setScale(2, RoundingMode.UNNECESSARY);
    }
}
