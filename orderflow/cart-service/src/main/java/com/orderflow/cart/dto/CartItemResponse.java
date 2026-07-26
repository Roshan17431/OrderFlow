package com.orderflow.cart.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CartItemResponse(
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
