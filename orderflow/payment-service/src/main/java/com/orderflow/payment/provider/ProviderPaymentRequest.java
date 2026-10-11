package com.orderflow.payment.provider;

import java.math.BigDecimal;
import java.util.UUID;

public record ProviderPaymentRequest(
        UUID paymentId,
        UUID orderId,
        BigDecimal amount,
        String currency,
        String idempotencyReference) {}
