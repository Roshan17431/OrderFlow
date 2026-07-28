package com.orderflow.inventory.dto;

import java.time.Instant;
import java.util.UUID;

public record InventoryAdjustmentResponse(
        UUID id,
        UUID productId,
        long quantityDelta,
        long resultingOnHand,
        long resultingReserved,
        String reason,
        String reference,
        UUID actorUserId,
        Instant createdAt,
        String correlationId) {}
