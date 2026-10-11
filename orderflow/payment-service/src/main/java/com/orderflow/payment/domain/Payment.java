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
@Table(name = "payment")
public class Payment {

    @Id
    private UUID id;
    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;
    @Column(name = "customer_id", nullable = false)
    private UUID customerId;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;
    @Column(nullable = false, length = 3)
    private String currency;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PaymentStatus status;
    @Column(nullable = false, length = 32)
    private String provider;
    @Column(name = "provider_payment_id", unique = true, length = 160)
    private String providerPaymentId;
    @Column(name = "provider_client_reference", length = 160)
    private String providerClientReference;
    @Column(name = "payment_deadline", nullable = false)
    private Instant paymentDeadline;
    @Column(name = "failure_code", length = 80)
    private String failureCode;
    @Enumerated(EnumType.STRING)
    @Column(name = "failure_category", length = 48)
    private ProviderFailureCategory failureCategory;
    @Column(name = "refunded_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal refundedAmount;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Column(name = "succeeded_at")
    private Instant succeededAt;
    @Column(name = "failed_at")
    private Instant failedAt;
    @Column(name = "cancelled_at")
    private Instant cancelledAt;
    @Version
    @Column(nullable = false)
    private long version;

    protected Payment() {}

    public static Payment create(
            UUID id, UUID orderId, UUID customerId, BigDecimal amount, String currency,
            String provider, String providerReference, Instant deadline, Instant now) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        if (currency == null || !currency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("currency must be a three-letter uppercase code");
        }
        if (deadline == null || !deadline.isAfter(now)) {
            throw new IllegalArgumentException("payment deadline must be in the future");
        }
        Payment payment = new Payment();
        payment.id = id;
        payment.orderId = java.util.Objects.requireNonNull(orderId);
        payment.customerId = java.util.Objects.requireNonNull(customerId);
        payment.amount = amount;
        payment.currency = currency;
        payment.status = PaymentStatus.PENDING;
        payment.provider = provider;
        payment.providerClientReference = providerReference;
        payment.paymentDeadline = deadline;
        payment.refundedAmount = BigDecimal.ZERO.setScale(2);
        payment.createdAt = now;
        payment.updatedAt = now;
        return payment;
    }

    public void changeStatus(PaymentStatus next, Instant now) {
        status = next;
        updatedAt = now;
        if (next == PaymentStatus.SUCCEEDED) {
            succeededAt = now;
            failureCode = null;
            failureCategory = null;
        } else if (next == PaymentStatus.FAILED) {
            failedAt = now;
        } else if (next == PaymentStatus.CANCELLED) {
            cancelledAt = now;
        }
    }

    public void bindProviderPayment(String providerPaymentId, Instant now) {
        if (this.providerPaymentId != null
                && !this.providerPaymentId.equals(providerPaymentId)) {
            throw new IllegalStateException("Provider payment reference cannot change");
        }
        this.providerPaymentId = providerPaymentId;
        this.updatedAt = now;
    }

    public void fail(String code, ProviderFailureCategory category, Instant now) {
        failureCode = code;
        failureCategory = category;
        failedAt = now;
        updatedAt = now;
    }

    public void applyRefund(BigDecimal refundAmount, Instant now) {
        BigDecimal cumulative = refundedAmount.add(refundAmount);
        if (refundAmount.signum() <= 0 || cumulative.compareTo(amount) > 0) {
            throw new IllegalArgumentException("refund exceeds the available amount");
        }
        refundedAmount = cumulative;
        updatedAt = now;
    }

    public BigDecimal refundableAmount() {
        return amount.subtract(refundedAmount);
    }

    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public UUID getCustomerId() { return customerId; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public PaymentStatus getStatus() { return status; }
    public String getProvider() { return provider; }
    public String getProviderPaymentId() { return providerPaymentId; }
    public String getProviderClientReference() { return providerClientReference; }
    public Instant getPaymentDeadline() { return paymentDeadline; }
    public String getFailureCode() { return failureCode; }
    public ProviderFailureCategory getFailureCategory() { return failureCategory; }
    public BigDecimal getRefundedAmount() { return refundedAmount; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getSucceededAt() { return succeededAt; }
    public Instant getFailedAt() { return failedAt; }
    public Instant getCancelledAt() { return cancelledAt; }
}
