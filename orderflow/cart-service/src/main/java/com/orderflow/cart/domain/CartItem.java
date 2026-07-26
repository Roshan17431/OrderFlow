package com.orderflow.cart.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CartItem(
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

    public BigDecimal lineTotal() {
        return unitPriceSnapshot.multiply(BigDecimal.valueOf(quantity));
    }
}
