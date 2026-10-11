package com.orderflow.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_attempt")
public class PaymentAttempt {
    @Id private UUID id;
    @Column(name = "payment_id", nullable = false) private UUID paymentId;
    @Column(name = "attempt_number", nullable = false) private int attemptNumber;
    @Column(nullable = false, length = 32) private String provider;
    @Column(name = "provider_attempt_id", length = 160) private String providerAttemptId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24) private PaymentAttemptStatus status;
    @Column(name = "failure_code", length = 80) private String failureCode;
    @Enumerated(EnumType.STRING)
    @Column(name = "failure_category", length = 48) private ProviderFailureCategory failureCategory;
    private Boolean retryable;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "completed_at") private Instant completedAt;
    @Column(name = "correlation_id", length = 128) private String correlationId;

    protected PaymentAttempt() {}

    public PaymentAttempt(
            UUID paymentId, int attemptNumber, String provider, String correlationId, Instant now) {
        if (attemptNumber <= 0) throw new IllegalArgumentException("attempt number must be positive");
        this.id = UUID.randomUUID();
        this.paymentId = paymentId;
        this.attemptNumber = attemptNumber;
        this.provider = provider;
        this.status = PaymentAttemptStatus.CREATED;
        this.correlationId = correlationId;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void submitted(String providerAttemptId, PaymentAttemptStatus status, Instant now) {
        this.providerAttemptId = providerAttemptId;
        this.status = status;
        this.updatedAt = now;
    }

    public void succeed(Instant now) {
        status = PaymentAttemptStatus.SUCCEEDED;
        updatedAt = now;
        completedAt = now;
    }

    public void fail(String code, ProviderFailureCategory category, boolean retryable, Instant now) {
        status = PaymentAttemptStatus.FAILED;
        failureCode = code;
        failureCategory = category;
        this.retryable = retryable;
        updatedAt = now;
        completedAt = now;
    }

    public void cancel(Instant now) {
        status = PaymentAttemptStatus.CANCELLED;
        updatedAt = now;
        completedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getPaymentId() { return paymentId; }
    public int getAttemptNumber() { return attemptNumber; }
    public PaymentAttemptStatus getStatus() { return status; }
    public String getFailureCode() { return failureCode; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCompletedAt() { return completedAt; }
}
