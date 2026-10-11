package com.orderflow.payment.provider;

import java.math.BigDecimal;
import java.util.UUID;

public record ProviderRefundRequest(
        UUID refundId,
        String providerPaymentId,
        BigDecimal amount,
        String currency,
        String idempotencyReference) {}
