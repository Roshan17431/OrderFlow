package com.orderflow.inventory.dto;

import com.orderflow.inventory.domain.ReservationStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReservationResponse(
        UUID id,
        UUID orderId,
        ReservationStatus status,
        Instant expiresAt,
        Instant releasedAt,
        List<ReservationItemResponse> items,
        long version) {}
