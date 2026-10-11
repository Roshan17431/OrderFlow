package com.orderflow.payment.provider;

public record ProviderCancellationResult(boolean confirmed, String providerPaymentId) {}
