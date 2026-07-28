package com.orderflow.order.dto;

import com.orderflow.order.domain.OrderStatus;
import java.time.Instant;
import java.util.UUID;

public record OrderHistoryResponse(
        OrderStatus previousStatus,
        OrderStatus newStatus,
        String reasonCode,
        UUID eventId,
        String correlationId,
        Instant changedAt) {}
