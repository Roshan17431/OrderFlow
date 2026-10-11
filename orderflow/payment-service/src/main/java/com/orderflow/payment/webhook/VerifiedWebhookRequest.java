package com.orderflow.payment.webhook;

import com.orderflow.payment.provider.VerifiedProviderWebhook;

public record VerifiedWebhookRequest(
        String providerEventId,
        String payloadHash,
        VerifiedProviderWebhook event) {}
