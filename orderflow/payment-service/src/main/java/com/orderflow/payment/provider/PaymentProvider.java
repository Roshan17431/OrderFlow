package com.orderflow.payment.provider;

public interface PaymentProvider {
    String name();
    String register(ProviderPaymentRequest request);
    ProviderPaymentResult confirm(
            ProviderPaymentRequest request, String opaquePaymentMethodToken);
    ProviderCancellationResult cancel(String providerPaymentId, String idempotencyReference);
    ProviderPaymentStatus lookup(String providerPaymentId);
    ProviderRefundResult refund(ProviderRefundRequest request);
    VerifiedProviderWebhook parseWebhook(byte[] verifiedPayload);
}
