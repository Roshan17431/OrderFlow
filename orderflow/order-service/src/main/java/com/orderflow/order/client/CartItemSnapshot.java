package com.orderflow.order.client;

import java.math.BigDecimal;
import java.time.Instant;
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
        Instant updatedAt) {}
