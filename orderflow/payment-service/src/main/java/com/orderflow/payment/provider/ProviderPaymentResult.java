package com.orderflow.payment.provider;

import com.orderflow.payment.domain.ProviderFailureCategory;

public record ProviderPaymentResult(
        Result result,
        String providerPaymentId,
        String providerAttemptId,
        String failureCode,
        ProviderFailureCategory failureCategory,
        boolean retryable) {

    public enum Result {
        PROCESSING,
        REQUIRES_ACTION,
        DECLINED
    }
}
