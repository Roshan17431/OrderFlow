package com.orderflow.payment.service;

import com.orderflow.events.EventMetadata;
import com.orderflow.events.EventType;
import com.orderflow.events.PaymentCancelRequestedEvent;
import com.orderflow.events.PaymentRequestedEvent;
import com.orderflow.payment.domain.Payment;
import com.orderflow.payment.domain.PaymentCancellationTombstone;
import com.orderflow.payment.domain.PaymentStateMachine;
import com.orderflow.payment.domain.PaymentStatus;
import com.orderflow.payment.domain.ProcessedEvent;
import com.orderflow.payment.provider.ProviderCancellationResult;
import com.orderflow.payment.provider.ProviderPaymentRequest;
import com.orderflow.payment.provider.ResilientPaymentProvider;
import com.orderflow.payment.repository.PaymentCancellationTombstoneRepository;
import com.orderflow.payment.repository.PaymentRepository;
import com.orderflow.payment.repository.ProcessedEventRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentOrderWorkflowService {
    static final String CONSUMER = "payment-order-events-v1";
    private final PaymentRepository payments;
    private final ProcessedEventRepository processed;
    private final PaymentCancellationTombstoneRepository tombstones;
    private final PaymentStateMachine stateMachine;
    private final ResilientPaymentProvider provider;
    private final RefundCreationSupport refundSupport;
    private final PaymentEventOutbox events;
    private final MeterRegistry metrics;
    private final Clock clock;

    public PaymentOrderWorkflowService(
            PaymentRepository payments,
            ProcessedEventRepository processed,
            PaymentCancellationTombstoneRepository tombstones,
            PaymentStateMachine stateMachine,
            ResilientPaymentProvider provider,
            RefundCreationSupport refundSupport,
            PaymentEventOutbox events,
            MeterRegistry metrics,
            Clock clock) {
        this.payments = payments;
        this.processed = processed;
        this.tombstones = tombstones;
        this.stateMachine = stateMachine;
        this.provider = provider;
        this.refundSupport = refundSupport;
        this.events = events;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Transactional
    public void create(PaymentRequestedEvent event) {
        event.metadata().require(EventType.PAYMENT_REQUESTED, PaymentRequestedEvent.VERSION);
        if (duplicate(event.metadata())) return;
        Payment existing = payments.findByOrderIdForUpdate(event.orderId()).orElse(null);
        if (existing != null) {
            if (!existing.getCustomerId().equals(event.customerId())
                    || existing.getAmount().compareTo(event.amount()) != 0
                    || !existing.getCurrency().equals(event.currency())) {
                throw new IllegalArgumentException("Conflicting payment request for order");
            }
            remember(event.metadata());
            return;
        }
        Instant now = Instant.now(clock);
        boolean cancelledBeforeCreation = tombstones.existsById(event.orderId());
        if (!cancelledBeforeCreation && !event.paymentDeadline().isAfter(now)) {
            throw new IllegalArgumentException("Payment deadline has expired");
        }
        UUID paymentId = UUID.randomUUID();
        Instant deadline = event.paymentDeadline().isAfter(now)
                ? event.paymentDeadline() : now.plusSeconds(1);
        Payment payment = Payment.create(
                paymentId, event.orderId(), event.customerId(), event.amount(),
                event.currency(), provider.rawProvider().name(),
                "order-" + event.orderId(), deadline, now);
        if (cancelledBeforeCreation) {
            stateMachine.transition(payment, PaymentStatus.CANCELLED, now);
            payments.save(payment);
            events.cancelled(
                    payment, "CANCELLED_BEFORE_PAYMENT_CREATION", now,
                    event.metadata().correlationId(), event.metadata().eventId());
        } else {
            String providerId = provider.register(request(payment, "register-" + paymentId));
            payment.bindProviderPayment(providerId, now);
            payments.save(payment);
            metrics.counter(
                    "orderflow_payment_creations_total",
                    "provider", payment.getProvider()).increment();
        }
        remember(event.metadata());
    }

    @Transactional
    public void cancel(PaymentCancelRequestedEvent event) {
        event.metadata().require(
                EventType.PAYMENT_CANCEL_REQUESTED, PaymentCancelRequestedEvent.VERSION);
        if (duplicate(event.metadata())) return;
        Instant now = Instant.now(clock);
        Payment payment = payments.findByOrderIdForUpdate(event.orderId()).orElse(null);
        if (payment == null) {
            tombstones.save(new PaymentCancellationTombstone(
                    event.orderId(), event.customerId(), event.paymentId(),
                    event.reasonCode(), event.requestedAt(),
                    event.metadata().correlationId()));
            remember(event.metadata());
            return;
        }
        switch (payment.getStatus()) {
            case PENDING, REQUIRES_ACTION, PROCESSING -> {
                stateMachine.transition(payment, PaymentStatus.CANCEL_REQUESTED, now);
                ProviderCancellationResult result = provider.cancel(
                        payment.getProviderPaymentId(), "event-" + event.metadata().eventId());
                if (result.confirmed()) {
                    stateMachine.transition(payment, PaymentStatus.CANCELLED, now);
                    events.cancelled(
                            payment, event.reasonCode(), now,
                            event.metadata().correlationId(), event.metadata().eventId());
                }
            }
            case FAILED -> {
                stateMachine.transition(payment, PaymentStatus.CANCELLED, now);
                events.cancelled(
                        payment, "NO_PAYMENT_TO_CANCEL", now,
                        event.metadata().correlationId(), event.metadata().eventId());
            }
            case SUCCEEDED, PARTIALLY_REFUNDED -> refundSupport.create(
                    payment, payment.refundableAmount(), "SYSTEM_COMPENSATION",
                    event.customerId(), "compensation-" + event.metadata().eventId(), now);
            case CANCEL_REQUESTED -> {
                ProviderCancellationResult result = provider.cancel(
                        payment.getProviderPaymentId(), "event-" + event.metadata().eventId());
                if (result.confirmed()) {
                    stateMachine.transition(payment, PaymentStatus.CANCELLED, now);
                    events.cancelled(
                            payment, event.reasonCode(), now,
                            event.metadata().correlationId(), event.metadata().eventId());
                }
            }
            case CANCELLED, REFUND_PENDING, REFUNDED -> {
                // Monotonic idempotent no-op.
            }
        }
        remember(event.metadata());
    }

    private ProviderPaymentRequest request(Payment payment, String reference) {
        return new ProviderPaymentRequest(
                payment.getId(), payment.getOrderId(), payment.getAmount(),
                payment.getCurrency(), reference);
    }

    private boolean duplicate(EventMetadata metadata) {
        boolean duplicate =
                processed.existsByEventIdAndConsumerName(metadata.eventId(), CONSUMER);
        if (duplicate) {
            metrics.counter(
                    "orderflow_consumer_duplicates_total", "service", "payment-service",
                    "eventType", metadata.eventType().name()).increment();
        }
        return duplicate;
    }

    private void remember(EventMetadata metadata) {
        processed.save(new ProcessedEvent(
                metadata.eventId(), metadata.eventType().name(), metadata.aggregateId(),
                CONSUMER, Instant.now(clock), metadata.correlationId()));
        metrics.counter(
                "orderflow_consumer_processed_total", "service", "payment-service",
                "eventType", metadata.eventType().name()).increment();
    }
}
