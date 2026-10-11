package com.orderflow.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "provider_webhook_event")
public class ProviderWebhookEvent {
    @Id private UUID id;
    @Column(nullable = false, length = 32) private String provider;
    @Column(name = "provider_event_id", nullable = false, length = 160)
    private String providerEventId;
    @Column(name = "event_type", nullable = false, length = 80) private String eventType;
    @Column(name = "payload_hash", nullable = false, length = 64) private String payloadHash;
    @Column(name = "payment_id") private UUID paymentId;
    @Column(name = "refund_id") private UUID refundId;
    @Column(name = "received_at", nullable = false) private Instant receivedAt;
    @Column(name = "processed_at") private Instant processedAt;
    @Column(name = "processing_status", nullable = false, length = 24)
    private String processingStatus;
    @Column(name = "failure_code", length = 80) private String failureCode;

    protected ProviderWebhookEvent() {}

    public ProviderWebhookEvent(
            String provider, String eventId, String eventType, String payloadHash,
            Instant receivedAt) {
        this.id = UUID.randomUUID();
        this.provider = provider;
        this.providerEventId = eventId;
        this.eventType = eventType;
        this.payloadHash = payloadHash;
        this.receivedAt = receivedAt;
        this.processingStatus = "RECEIVED";
    }

    public void processed(UUID paymentId, UUID refundId, Instant now) {
        this.paymentId = paymentId;
        this.refundId = refundId;
        this.processedAt = now;
        this.processingStatus = "PROCESSED";
    }

    public String getPayloadHash() { return payloadHash; }
    public String getProcessingStatus() { return processingStatus; }
}
