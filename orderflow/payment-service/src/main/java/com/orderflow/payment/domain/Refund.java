package com.orderflow.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refund")
public class Refund {
    @Id private UUID id;
    @Column(name = "payment_id", nullable = false) private UUID paymentId;
    @Column(name = "order_id", nullable = false) private UUID orderId;
    @Column(name = "customer_id", nullable = false) private UUID customerId;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal amount;
    @Column(nullable = false, length = 3) private String currency;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24) private RefundStatus status;
    @Column(name = "reason_code", nullable = false, length = 80) private String reasonCode;
    @Column(name = "provider_refund_id", unique = true, length = 160) private String providerRefundId;
    @Column(name = "failure_code", length = 80) private String failureCode;
    @Column(name = "created_by", nullable = false) private UUID createdBy;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "succeeded_at") private Instant succeededAt;
    @Column(name = "failed_at") private Instant failedAt;
    @Version @Column(nullable = false) private long version;

    protected Refund() {}

    public Refund(
            Payment payment, BigDecimal amount, String reasonCode, UUID createdBy, Instant now) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("refund amount must be positive");
        }
        if (amount.compareTo(payment.refundableAmount()) > 0) {
            throw new IllegalArgumentException("refund exceeds the available amount");
        }
        this.id = UUID.randomUUID();
        this.paymentId = payment.getId();
        this.orderId = payment.getOrderId();
        this.customerId = payment.getCustomerId();
        this.amount = amount;
        this.currency = payment.getCurrency();
        this.status = RefundStatus.PENDING;
        this.reasonCode = reasonCode;
        this.createdBy = createdBy;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void processing(String providerRefundId, Instant now) {
        this.providerRefundId = providerRefundId;
        status = RefundStatus.PROCESSING;
        updatedAt = now;
    }

    public void succeed(Instant now) {
        status = RefundStatus.SUCCEEDED;
        succeededAt = now;
        updatedAt = now;
    }

    public void fail(String code, Instant now) {
        status = RefundStatus.FAILED;
        failureCode = code;
        failedAt = now;
        updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getPaymentId() { return paymentId; }
    public UUID getOrderId() { return orderId; }
    public UUID getCustomerId() { return customerId; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public RefundStatus getStatus() { return status; }
    public String getReasonCode() { return reasonCode; }
    public String getProviderRefundId() { return providerRefundId; }
    public String getFailureCode() { return failureCode; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getSucceededAt() { return succeededAt; }
    public Instant getFailedAt() { return failedAt; }
}
