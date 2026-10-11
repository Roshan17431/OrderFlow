package com.orderflow.payment.dto;

import com.orderflow.payment.domain.RefundStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RefundResponse(
        UUID id,
        UUID paymentId,
        BigDecimal amount,
        String currency,
        RefundStatus status,
        String reason,
        String failureCode,
        Instant createdAt,
        Instant updatedAt,
        Instant succeededAt,
        Instant failedAt) {}
