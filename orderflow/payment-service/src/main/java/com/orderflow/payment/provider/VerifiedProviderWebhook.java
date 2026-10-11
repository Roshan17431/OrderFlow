package com.orderflow.payment.provider;

import com.orderflow.payment.domain.ProviderFailureCategory;

public record VerifiedProviderWebhook(
        String eventType,
        String providerPaymentId,
        String providerRefundId,
        String failureCode,
        ProviderFailureCategory failureCategory,
        boolean retryable) {}
