package com.orderflow.order.service;

import com.orderflow.events.EventMetadata;
import com.orderflow.events.EventType;
import com.orderflow.events.DeliveryCancelRequestedEvent;
import com.orderflow.events.DeliveryRequestedEvent;
import com.orderflow.events.OrderCancelRequestedEvent;
import com.orderflow.events.PaymentCancelRequestedEvent;
import com.orderflow.events.PaymentCancelledEvent;
import com.orderflow.events.PaymentFailedEvent;
import com.orderflow.events.PaymentRefundFailedEvent;
import com.orderflow.events.PaymentRefundedEvent;
import com.orderflow.events.PaymentSucceededEvent;
import com.orderflow.order.config.KafkaTopicProperties;
import com.orderflow.order.domain.CustomerOrder;
import com.orderflow.order.domain.OrderStateMachine;
import com.orderflow.order.domain.OrderStatus;
import com.orderflow.order.domain.ProcessedEvent;
import com.orderflow.order.outbox.OutboxStore;
import com.orderflow.order.repository.CustomerOrderRepository;
import com.orderflow.order.repository.ProcessedEventRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderPaymentWorkflowService {
    static final String CONSUMER = "order-payment-events-v1";
    private final CustomerOrderRepository orders;
    private final ProcessedEventRepository processed;
    private final OrderStateMachine stateMachine;
    private final OutboxStore outbox;
    private final KafkaTopicProperties topics;
    private final MeterRegistry metrics;
    private final Clock clock;

    public OrderPaymentWorkflowService(
            CustomerOrderRepository orders,
            ProcessedEventRepository processed,
            OrderStateMachine stateMachine,
            OutboxStore outbox,
            KafkaTopicProperties topics,
            MeterRegistry metrics,
            Clock clock) {
        this.orders = orders;
        this.processed = processed;
        this.stateMachine = stateMachine;
        this.outbox = outbox;
        this.topics = topics;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Transactional
    public void succeeded(PaymentSucceededEvent event) {
        event.metadata().require(EventType.PAYMENT_SUCCEEDED, PaymentSucceededEvent.VERSION);
        if (duplicate(event.metadata())) return;
        CustomerOrder order = requireOrder(event.orderId());
        validate(event.metadata(), order, event.customerId());
        Instant now = Instant.now(clock);
        if (order.getStatus() == OrderStatus.PAYMENT_PENDING) {
            order.paymentSucceeded(event.paymentId(), event.succeededAt());
            transition(order, OrderStatus.PAID, "PAYMENT_SUCCEEDED", event.metadata(), now);
            order.requestDelivery(now);
            enqueueDeliveryRequest(order, event.metadata(), now);
        } else if (Set.of(OrderStatus.CANCEL_REQUESTED, OrderStatus.CANCELLED)
                .contains(order.getStatus())) {
            order.paymentSucceeded(event.paymentId(), event.succeededAt());
            enqueuePaymentCancel(
                    order, "SYSTEM_COMPENSATION", event.metadata(), now);
        }
        remember(event.metadata());
    }

    @Transactional
    public void failed(PaymentFailedEvent event) {
        event.metadata().require(EventType.PAYMENT_FAILED, PaymentFailedEvent.VERSION);
        if (duplicate(event.metadata())) return;
        CustomerOrder order = requireOrder(event.orderId());
        validate(event.metadata(), order, event.customerId());
        Instant now = Instant.now(clock);
        if (order.getStatus() == OrderStatus.PAYMENT_PENDING) {
            order.paymentFailed(event.paymentId(), event.failureCode(), event.failedAt());
            transition(order, OrderStatus.PAYMENT_FAILED, "PAYMENT_FAILED", event.metadata(), now);
            enqueueInventoryRelease(order, "PAYMENT_FAILED", event.metadata(), now);
        }
        remember(event.metadata());
    }

    @Transactional
    public void cancelled(PaymentCancelledEvent event) {
        event.metadata().require(EventType.PAYMENT_CANCELLED, PaymentCancelledEvent.VERSION);
        if (duplicate(event.metadata())) return;
        CustomerOrder order = requireOrder(event.orderId());
        validate(event.metadata(), order, event.customerId());
        Instant now = Instant.now(clock);
        if (order.getStatus() == OrderStatus.PAYMENT_PENDING) {
            transition(
                    order, OrderStatus.CANCEL_REQUESTED,
                    "PAYMENT_CANCELLED", event.metadata(), now);
            enqueueInventoryRelease(order, "PAYMENT_CANCELLED", event.metadata(), now);
        }
        remember(event.metadata());
    }

    @Transactional
    public void refunded(PaymentRefundedEvent event) {
        event.metadata().require(EventType.PAYMENT_REFUNDED, PaymentRefundedEvent.VERSION);
        if (duplicate(event.metadata())) return;
        CustomerOrder order = requireOrder(event.orderId());
        validate(event.metadata(), order, event.customerId());
        Instant now = Instant.now(clock);
        if (Set.of(OrderStatus.PAID, OrderStatus.PARTIALLY_REFUNDED)
                .contains(order.getStatus())) {
            order.paymentRefunded(event.cumulativeRefundedAmount(), event.refundedAt());
            transition(
                    order,
                    event.fullRefund()
                            ? OrderStatus.REFUNDED : OrderStatus.PARTIALLY_REFUNDED,
                    event.fullRefund() ? "PAYMENT_REFUNDED" : "PAYMENT_PARTIALLY_REFUNDED",
                    event.metadata(),
                    now);
            if (event.fullRefund()
                    && Set.of(com.orderflow.order.domain.FulfillmentStatus.DELIVERY_PENDING,
                            com.orderflow.order.domain.FulfillmentStatus.DELIVERY_ASSIGNED,
                            com.orderflow.order.domain.FulfillmentStatus.PICKED_UP)
                    .contains(order.getFulfillmentStatus())) {
                order.requestDeliveryCancellation(now);
                enqueueDeliveryCancellation(order, event.metadata(), now);
            }
        } else if (order.getStatus() == OrderStatus.CANCELLED && event.fullRefund()) {
            order.paymentRefunded(event.cumulativeRefundedAmount(), event.refundedAt());
            transition(
                    order, OrderStatus.REFUNDED, "COMPENSATION_REFUNDED",
                    event.metadata(), now);
        }
        remember(event.metadata());
    }

    @Transactional
    public void refundFailed(PaymentRefundFailedEvent event) {
        event.metadata().require(
                EventType.PAYMENT_REFUND_FAILED, PaymentRefundFailedEvent.VERSION);
        if (duplicate(event.metadata())) return;
        CustomerOrder order = requireOrder(event.orderId());
        validate(event.metadata(), order, event.customerId());
        order.recordWorkflowEvent(
                "PAYMENT_REFUND_FAILED:" + event.failureCode(),
                event.metadata().eventId(), event.metadata().correlationId(),
                Instant.now(clock));
        remember(event.metadata());
    }

    private void enqueueInventoryRelease(
            CustomerOrder order, String reason, EventMetadata cause, Instant now) {
        EventMetadata metadata = metadata(
                EventType.ORDER_CANCEL_REQUESTED, OrderCancelRequestedEvent.VERSION,
                order, cause, now);
        outbox.enqueue(
                topics.orderEvents(), order.getId(),
                new OrderCancelRequestedEvent(
                        metadata, order.getId(), order.getCustomerId(), reason),
                metadata);
    }

    private void enqueueDeliveryRequest(
            CustomerOrder order, EventMetadata cause, Instant now) {
        EventMetadata metadata = metadata(EventType.DELIVERY_REQUESTED,
                DeliveryRequestedEvent.VERSION, order, cause, now);
        outbox.enqueue(topics.deliveryEvents(), order.getId(),
                new DeliveryRequestedEvent(metadata, order.getId(), order.getCustomerId(),
                        order.getShippingAddress().snapshot(), now), metadata);
    }

    private void enqueueDeliveryCancellation(
            CustomerOrder order, EventMetadata cause, Instant now) {
        EventMetadata metadata = metadata(EventType.DELIVERY_CANCEL_REQUESTED,
                DeliveryCancelRequestedEvent.VERSION, order, cause, now);
        outbox.enqueue(topics.deliveryEvents(), order.getId(),
                new DeliveryCancelRequestedEvent(metadata, order.getId(), order.getCustomerId(),
                        order.getDeliveryId(), "FULL_REFUND", now), metadata);
    }

    private void enqueuePaymentCancel(
            CustomerOrder order, String reason, EventMetadata cause, Instant now) {
        EventMetadata metadata = metadata(
                EventType.PAYMENT_CANCEL_REQUESTED,
                PaymentCancelRequestedEvent.VERSION, order, cause, now);
        outbox.enqueue(
                topics.orderEvents(), order.getId(),
                new PaymentCancelRequestedEvent(
                        metadata, order.getId(), order.getCustomerId(),
                        order.getPaymentId(), reason, now),
                metadata);
    }

    private EventMetadata metadata(
            EventType type, int version, CustomerOrder order,
            EventMetadata cause, Instant now) {
        return new EventMetadata(
                UUID.randomUUID(), type, version, "ORDER", order.getId(), now,
                cause.correlationId(), cause.eventId(), "order-service");
    }

    private void transition(
            CustomerOrder order, OrderStatus status, String reason,
            EventMetadata event, Instant now) {
        stateMachine.transition(
                order, status, reason, event.eventId(), event.correlationId(), now);
        metrics.counter(
                "orderflow_order_state_transitions_total",
                "orderStatus", status.name()).increment();
    }

    private CustomerOrder requireOrder(UUID orderId) {
        return orders.findByIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Payment order does not exist"));
    }

    private void validate(EventMetadata metadata, CustomerOrder order, UUID customerId) {
        if (!metadata.aggregateId().equals(order.getId())
                || !customerId.equals(order.getCustomerId())) {
            throw new IllegalArgumentException("Payment event aggregate does not match order");
        }
    }

    private boolean duplicate(EventMetadata metadata) {
        boolean duplicate =
                processed.existsByEventIdAndConsumerName(metadata.eventId(), CONSUMER);
        if (duplicate) {
            metrics.counter(
                    "orderflow_consumer_duplicates_total", "service", "order-service",
                    "eventType", metadata.eventType().name()).increment();
        }
        return duplicate;
    }

    private void remember(EventMetadata metadata) {
        processed.save(new ProcessedEvent(
                metadata.eventId(), metadata.eventType().name(), metadata.aggregateId(),
                CONSUMER, Instant.now(clock), metadata.correlationId()));
        metrics.counter(
                "orderflow_consumer_processed_total", "service", "order-service",
                "eventType", metadata.eventType().name()).increment();
    }
}
