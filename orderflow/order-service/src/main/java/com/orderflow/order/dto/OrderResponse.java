package com.orderflow.order.dto;

import com.orderflow.order.domain.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        UUID customerId,
        OrderStatus status,
        String currency,
        BigDecimal subtotal,
        BigDecimal totalAmount,
        long cartVersion,
        String cancellationReason,
        UUID inventoryReservationId,
        Instant inventoryReservationExpiresAt,
        List<OrderItemResponse> items,
        Instant createdAt,
        Instant updatedAt,
        long version) {
    public OrderResponse {
        items = List.copyOf(items);
    }
}
