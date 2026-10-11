package com.orderflow.payment.service;

import com.orderflow.events.EventMetadata;
import com.orderflow.events.EventType;
import com.orderflow.events.PaymentCancelledEvent;
import com.orderflow.events.PaymentFailedEvent;
import com.orderflow.events.PaymentRefundFailedEvent;
import com.orderflow.events.PaymentRefundedEvent;
import com.orderflow.events.PaymentSucceededEvent;
import com.orderflow.payment.config.KafkaTopicProperties;
import com.orderflow.payment.domain.Payment;
import com.orderflow.payment.domain.Refund;
import com.orderflow.payment.outbox.OutboxStore;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventOutbox {
    private final OutboxStore outbox;
    private final KafkaTopicProperties topics;

    public PaymentEventOutbox(OutboxStore outbox, KafkaTopicProperties topics) {
        this.outbox = outbox;
        this.topics = topics;
    }

    public void succeeded(Payment payment, Instant now, String correlationId, UUID causationId) {
        EventMetadata metadata = metadata(
                EventType.PAYMENT_SUCCEEDED, PaymentSucceededEvent.VERSION,
                payment.getOrderId(), now, correlationId, causationId);
        outbox.enqueue(
                topics.paymentEvents(), payment.getOrderId(),
                new PaymentSucceededEvent(
                        metadata, payment.getId(), payment.getOrderId(),
                        payment.getCustomerId(), payment.getProvider(),
                        payment.getProviderPaymentId(), payment.getAmount(),
                        payment.getCurrency(), now),
                metadata);
    }

    public void failed(
            Payment payment, boolean retryable, Instant now,
            String correlationId, UUID causationId) {
        EventMetadata metadata = metadata(
                EventType.PAYMENT_FAILED, PaymentFailedEvent.VERSION,
                payment.getOrderId(), now, correlationId, causationId);
        outbox.enqueue(
                topics.paymentEvents(), payment.getOrderId(),
                new PaymentFailedEvent(
                        metadata, payment.getId(), payment.getOrderId(),
                        payment.getCustomerId(), payment.getProvider(),
                        payment.getFailureCode(), payment.getFailureCategory().name(),
                        retryable, now),
                metadata);
    }

    public void cancelled(
            Payment payment, String reason, Instant now,
            String correlationId, UUID causationId) {
        EventMetadata metadata = metadata(
                EventType.PAYMENT_CANCELLED, PaymentCancelledEvent.VERSION,
                payment.getOrderId(), now, correlationId, causationId);
        outbox.enqueue(
                topics.paymentEvents(), payment.getOrderId(),
                new PaymentCancelledEvent(
                        metadata, payment.getId(), payment.getOrderId(),
                        payment.getCustomerId(), payment.getProvider(), reason, now),
                metadata);
    }

    public void refunded(
            Payment payment, Refund refund, Instant now,
            String correlationId, UUID causationId) {
        boolean full = payment.getRefundedAmount().compareTo(payment.getAmount()) == 0;
        EventMetadata metadata = metadata(
                EventType.PAYMENT_REFUNDED, PaymentRefundedEvent.VERSION,
                payment.getOrderId(), now, correlationId, causationId);
        outbox.enqueue(
                topics.paymentEvents(), payment.getOrderId(),
                new PaymentRefundedEvent(
                        metadata, payment.getId(), refund.getId(), payment.getOrderId(),
                        payment.getCustomerId(), refund.getAmount(),
                        payment.getRefundedAmount(), payment.getAmount(),
                        payment.getCurrency(), full, now),
                metadata);
    }

    public void refundFailed(
            Payment payment, Refund refund, boolean retryable, Instant now,
            String correlationId, UUID causationId) {
        EventMetadata metadata = metadata(
                EventType.PAYMENT_REFUND_FAILED, PaymentRefundFailedEvent.VERSION,
                payment.getOrderId(), now, correlationId, causationId);
        outbox.enqueue(
                topics.paymentEvents(), payment.getOrderId(),
                new PaymentRefundFailedEvent(
                        metadata, payment.getId(), refund.getId(), payment.getOrderId(),
                        payment.getCustomerId(), refund.getAmount(),
                        payment.getCurrency(), refund.getFailureCode(), retryable, now),
                metadata);
    }

    private EventMetadata metadata(
            EventType type, int version, UUID orderId, Instant now,
            String correlationId, UUID causationId) {
        return new EventMetadata(
                UUID.randomUUID(), type, version, "ORDER", orderId, now,
                correlationId, causationId, "payment-service");
    }
}
