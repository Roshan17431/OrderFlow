package com.orderflow.payment.service;

import com.orderflow.payment.domain.Payment;
import com.orderflow.payment.domain.PaymentStateMachine;
import com.orderflow.payment.domain.PaymentStatus;
import com.orderflow.payment.domain.ProviderFailureCategory;
import com.orderflow.payment.domain.ProviderWebhookEvent;
import com.orderflow.payment.domain.Refund;
import com.orderflow.payment.domain.RefundStatus;
import com.orderflow.payment.error.PaymentDomainException;
import com.orderflow.payment.provider.VerifiedProviderWebhook;
import com.orderflow.payment.repository.PaymentAttemptRepository;
import com.orderflow.payment.repository.PaymentRepository;
import com.orderflow.payment.repository.ProviderWebhookEventRepository;
import com.orderflow.payment.repository.RefundRepository;
import com.orderflow.payment.webhook.VerifiedWebhookRequest;
import com.orderflow.platform.error.ErrorCode;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentWebhookService {
    private final ProviderWebhookEventRepository webhookEvents;
    private final PaymentRepository payments;
    private final PaymentAttemptRepository attempts;
    private final RefundRepository refunds;
    private final PaymentStateMachine stateMachine;
    private final RefundCreationSupport refundSupport;
    private final PaymentEventOutbox events;
    private final MeterRegistry metrics;
    private final Clock clock;

    public PaymentWebhookService(
            ProviderWebhookEventRepository webhookEvents,
            PaymentRepository payments,
            PaymentAttemptRepository attempts,
            RefundRepository refunds,
            PaymentStateMachine stateMachine,
            RefundCreationSupport refundSupport,
            PaymentEventOutbox events,
            MeterRegistry metrics,
            Clock clock) {
        this.webhookEvents = webhookEvents;
        this.payments = payments;
        this.attempts = attempts;
        this.refunds = refunds;
        this.stateMachine = stateMachine;
        this.refundSupport = refundSupport;
        this.events = events;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Transactional
    public void process(VerifiedWebhookRequest request, String correlationId) {
        ProviderWebhookEvent existing = webhookEvents
                .findByProviderAndProviderEventId("SIMULATOR", request.providerEventId())
                .orElse(null);
        if (existing != null) {
            if (!existing.getPayloadHash().equals(request.payloadHash())) {
                metrics.counter(
                        "orderflow_payment_webhook_replay_conflicts_total",
                        "provider", "SIMULATOR").increment();
                throw new PaymentDomainException(
                        HttpStatus.CONFLICT,
                        ErrorCode.PAYMENT_WEBHOOK_EVENT_CONFLICT,
                        "The provider event ID was reused with different content.");
            }
            metrics.counter(
                    "orderflow_payment_webhook_duplicates_total",
                    "provider", "SIMULATOR").increment();
            return;
        }
        Instant now = Instant.now(clock);
        ProviderWebhookEvent stored = new ProviderWebhookEvent(
                "SIMULATOR", request.providerEventId(), request.event().eventType(),
                request.payloadHash(), now);
        webhookEvents.saveAndFlush(stored);
        UUID paymentId = null;
        UUID refundId = null;
        if (request.event().eventType().startsWith("payment.")) {
            Payment payment = requirePayment(request.event().providerPaymentId());
            paymentId = payment.getId();
            processPayment(payment, request.event(), correlationId, now);
        } else {
            Refund refund = refunds.findByProviderRefundId(
                            request.event().providerRefundId())
                    .orElseThrow(() -> new PaymentDomainException(
                            HttpStatus.NOT_FOUND, ErrorCode.REFUND_NOT_FOUND,
                            "Refund was not found."));
            refundId = refund.getId();
            paymentId = refund.getPaymentId();
            processRefund(refund, request.event(), correlationId, now);
        }
        stored.processed(paymentId, refundId, now);
        metrics.counter(
                "orderflow_payment_webhooks_processed_total",
                "provider", "SIMULATOR", "eventType", request.event().eventType()).increment();
    }

    @Transactional
    public void reconciledSuccess(UUID paymentId, String correlationId) {
        Payment payment = payments.findByIdForUpdate(paymentId).orElseThrow();
        paymentSucceeded(payment, correlationId, Instant.now(clock));
    }

    @Transactional
    public void reconciledFailure(UUID paymentId, String code, String correlationId) {
        Payment payment = payments.findByIdForUpdate(paymentId).orElseThrow();
        paymentFailed(payment, new VerifiedProviderWebhook("payment.failed", payment.getProviderPaymentId(),
                null, code, ProviderFailureCategory.UNKNOWN, false), correlationId, Instant.now(clock));
    }

    private void processPayment(
            Payment payment, VerifiedProviderWebhook webhook,
            String correlationId, Instant now) {
        switch (webhook.eventType()) {
            case "payment.succeeded" -> paymentSucceeded(payment, correlationId, now);
            case "payment.failed" -> paymentFailed(payment, webhook, correlationId, now);
            case "payment.cancelled" -> paymentCancelled(payment, correlationId, now);
            default -> throw unsupported();
        }
    }

    private void paymentSucceeded(Payment payment, String correlationId, Instant now) {
        if (Set.of(
                        PaymentStatus.SUCCEEDED,
                        PaymentStatus.PARTIALLY_REFUNDED,
                        PaymentStatus.REFUND_PENDING,
                        PaymentStatus.REFUNDED)
                .contains(payment.getStatus())) {
            return;
        }
        attempts.findFirstByPaymentIdOrderByAttemptNumberDesc(payment.getId())
                .ifPresent(attempt -> attempt.succeed(now));
        boolean compensation = Set.of(
                        PaymentStatus.CANCEL_REQUESTED, PaymentStatus.CANCELLED)
                .contains(payment.getStatus())
                || !payment.getPaymentDeadline().isAfter(now);
        if (compensation) {
            if (Set.of(PaymentStatus.PROCESSING, PaymentStatus.REQUIRES_ACTION)
                    .contains(payment.getStatus())) {
                stateMachine.transition(payment, PaymentStatus.SUCCEEDED, now);
            }
            refundSupport.create(
                    payment, payment.refundableAmount(), "SYSTEM_COMPENSATION",
                    payment.getCustomerId(), "late-success-" + payment.getId(), now);
            metrics.counter(
                    "orderflow_payment_compensation_refunds_total",
                    "provider", payment.getProvider()).increment();
            if (!payment.getPaymentDeadline().isAfter(now)) {
                metrics.counter(
                        "orderflow_payment_late_successes_total",
                        "provider", payment.getProvider()).increment();
            }
            return;
        }
        if (Set.of(
                        PaymentStatus.PROCESSING, PaymentStatus.REQUIRES_ACTION)
                .contains(payment.getStatus())) {
            stateMachine.transition(payment, PaymentStatus.SUCCEEDED, now);
            events.succeeded(payment, now, correlationId, null);
            metrics.counter(
                    "orderflow_payment_successes_total",
                    "provider", payment.getProvider()).increment();
        }
    }

    private void paymentFailed(
            Payment payment, VerifiedProviderWebhook webhook,
            String correlationId, Instant now) {
        if (!Set.of(PaymentStatus.PROCESSING, PaymentStatus.REQUIRES_ACTION)
                .contains(payment.getStatus())) {
            return;
        }
        String code = webhook.failureCode() == null
                ? "PROVIDER_PAYMENT_FAILED" : webhook.failureCode();
        ProviderFailureCategory category = webhook.failureCategory() == null
                ? ProviderFailureCategory.UNKNOWN : webhook.failureCategory();
        attempts.findFirstByPaymentIdOrderByAttemptNumberDesc(payment.getId())
                .ifPresent(attempt -> attempt.fail(code, category, webhook.retryable(), now));
        payment.fail(code, category, now);
        stateMachine.transition(payment, PaymentStatus.FAILED, now);
        events.failed(payment, webhook.retryable(), now, correlationId, null);
        metrics.counter(
                "orderflow_payment_failures_total",
                "provider", payment.getProvider(), "failureCategory", category.name()).increment();
    }

    private void paymentCancelled(Payment payment, String correlationId, Instant now) {
        if (payment.getStatus() != PaymentStatus.CANCEL_REQUESTED) return;
        stateMachine.transition(payment, PaymentStatus.CANCELLED, now);
        attempts.findFirstByPaymentIdOrderByAttemptNumberDesc(payment.getId())
                .ifPresent(attempt -> attempt.cancel(now));
        events.cancelled(payment, "PROVIDER_CONFIRMED", now, correlationId, null);
    }

    private void processRefund(
            Refund refund, VerifiedProviderWebhook webhook,
            String correlationId, Instant now) {
        if (refund.getStatus() == RefundStatus.SUCCEEDED
                || refund.getStatus() == RefundStatus.FAILED) {
            return;
        }
        Payment payment = payments.findByIdForUpdate(refund.getPaymentId())
                .orElseThrow(() -> new IllegalArgumentException("Refund payment is missing"));
        if ("refund.succeeded".equals(webhook.eventType())) {
            refund.succeed(now);
            payment.applyRefund(refund.getAmount(), now);
            PaymentStatus next = payment.getRefundedAmount().compareTo(payment.getAmount()) == 0
                    ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED;
            stateMachine.transition(payment, next, now);
            events.refunded(payment, refund, now, correlationId, null);
            metrics.counter(
                    "orderflow_payment_refund_successes_total",
                    "provider", payment.getProvider(), "refundStatus", next.name()).increment();
        } else if ("refund.failed".equals(webhook.eventType())) {
            refund.fail(
                    webhook.failureCode() == null
                            ? "PROVIDER_REFUND_FAILED" : webhook.failureCode(),
                    now);
            PaymentStatus restore = payment.getRefundedAmount().signum() == 0
                    ? PaymentStatus.SUCCEEDED : PaymentStatus.PARTIALLY_REFUNDED;
            stateMachine.transition(payment, restore, now);
            events.refundFailed(
                    payment, refund, webhook.retryable(), now, correlationId, null);
            metrics.counter(
                    "orderflow_payment_refund_failures_total",
                    "provider", payment.getProvider()).increment();
        } else {
            throw unsupported();
        }
    }

    private Payment requirePayment(String providerPaymentId) {
        if (providerPaymentId == null || providerPaymentId.isBlank()) {
            throw new PaymentDomainException(
                    HttpStatus.BAD_REQUEST, ErrorCode.PAYMENT_EVENT_UNSUPPORTED,
                    "Webhook payment reference is missing.");
        }
        Payment found = payments.findByProviderPaymentId(providerPaymentId)
                .orElseThrow(() -> new PaymentDomainException(
                        HttpStatus.NOT_FOUND, ErrorCode.PAYMENT_NOT_FOUND,
                        "Payment was not found."));
        return payments.findByIdForUpdate(found.getId()).orElseThrow();
    }

    private PaymentDomainException unsupported() {
        return new PaymentDomainException(
                HttpStatus.BAD_REQUEST, ErrorCode.PAYMENT_EVENT_UNSUPPORTED,
                "The webhook event type is unsupported.");
    }
}
