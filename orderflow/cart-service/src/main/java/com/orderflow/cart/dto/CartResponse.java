package com.orderflow.cart.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CartResponse(
        UUID userId,
        long version,
        String currency,
        List<CartItemResponse> items,
        int distinctItemCount,
        int totalQuantity,
        BigDecimal subtotal,
        Instant createdAt,
        Instant updatedAt,
        Instant expiresAt) {

    public CartResponse {
        items = List.copyOf(items);
    }
}
