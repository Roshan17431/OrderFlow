package com.orderflow.payment.dto;

import com.orderflow.payment.domain.PaymentAttemptStatus;
import java.time.Instant;

public record PaymentAttemptResponse(
        int attemptNumber,
        PaymentAttemptStatus status,
        String failureCode,
        Instant createdAt,
        Instant completedAt) {}
