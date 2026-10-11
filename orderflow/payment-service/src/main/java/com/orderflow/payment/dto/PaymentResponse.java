package com.orderflow.payment.dto;

import com.orderflow.payment.domain.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        UUID orderId,
        PaymentStatus status,
        BigDecimal amount,
        String currency,
        String provider,
        BigDecimal refundedAmount,
        String failureCode,
        Instant paymentDeadline,
        Instant createdAt,
        Instant updatedAt,
        Instant succeededAt,
        Instant failedAt,
        Instant cancelledAt) {}
