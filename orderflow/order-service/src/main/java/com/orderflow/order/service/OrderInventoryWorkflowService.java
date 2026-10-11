package com.orderflow.order.service;

import com.orderflow.events.EventMetadata;
import com.orderflow.events.EventType;
import com.orderflow.events.InventoryReleasedEvent;
import com.orderflow.events.InventoryReservationFailedEvent;
import com.orderflow.events.InventoryReservedEvent;
import com.orderflow.events.InventoryCommittedEvent;
import com.orderflow.events.DeliveryRequestedEvent;
import com.orderflow.order.domain.FulfillmentStatus;
import com.orderflow.events.PaymentCancelRequestedEvent;
import com.orderflow.events.PaymentRequestedEvent;
import com.orderflow.order.config.KafkaTopicProperties;
import com.orderflow.order.config.OrderProperties;
import com.orderflow.order.domain.CustomerOrder;
import com.orderflow.order.domain.OrderStateMachine;
import com.orderflow.order.domain.OrderStatus;
import com.orderflow.order.domain.ProcessedEvent;
import com.orderflow.order.repository.CustomerOrderRepository;
import com.orderflow.order.repository.ProcessedEventRepository;
import com.orderflow.order.outbox.OutboxStore;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderInventoryWorkflowService {

    static final String INVENTORY_CONSUMER = "order-inventory-events-v1";
    private static final Logger LOGGER =
            LoggerFactory.getLogger(OrderInventoryWorkflowService.class);
    private final CustomerOrderRepository orders;
    private final ProcessedEventRepository processed;
    private final OrderStateMachine stateMachine;
    private final MeterRegistry metrics;
    private final OutboxStore outbox;
    private final KafkaTopicProperties topics;
    private final OrderProperties properties;
    private final Clock clock;

    public OrderInventoryWorkflowService(
            CustomerOrderRepository orders,
            ProcessedEventRepository processed,
            OrderStateMachine stateMachine,
            MeterRegistry metrics,
            OutboxStore outbox,
            KafkaTopicProperties topics,
            OrderProperties properties,
            Clock clock) {
        this.orders = orders;
        this.processed = processed;
        this.stateMachine = stateMachine;
        this.metrics = metrics;
        this.outbox = outbox;
        this.topics = topics;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public void reserved(InventoryReservedEvent event) {
        event.metadata().require(EventType.INVENTORY_RESERVED, InventoryReservedEvent.VERSION);
        if (duplicate(event.metadata())) {
            return;
        }
        CustomerOrder order = requireOrder(event.orderId());
        validateAggregate(event.metadata(), order);
        Instant now = Instant.now(clock);
        if (order.getStatus() == OrderStatus.INVENTORY_PENDING) {
            order.attachReservation(event.reservationId(), event.expiresAt(), now);
            transition(
                    order,
                    OrderStatus.INVENTORY_RESERVED,
                    "INVENTORY_RESERVED",
                    event.metadata(),
                    now);
            transition(
                    order,
                    OrderStatus.PAYMENT_PENDING,
                    "PAYMENT_REQUIRED",
                    event.metadata(),
                    now);
            Instant deadline = now.plus(properties.paymentWindow());
            if (deadline.isAfter(event.expiresAt())) {
                deadline = event.expiresAt();
            }
            if (order.getTotalAmount().signum() <= 0
                    || !deadline.isAfter(now.plus(properties.minimumPaymentWindow()))) {
                transition(
                        order,
                        OrderStatus.CANCEL_REQUESTED,
                        "PAYMENT_WINDOW_UNAVAILABLE",
                        event.metadata(),
                        now);
                enqueuePaymentCancel(
                        order, "INVENTORY_EXPIRED", event.metadata(), now);
            } else {
                EventMetadata metadata = new EventMetadata(
                        java.util.UUID.randomUUID(),
                        EventType.PAYMENT_REQUESTED,
                        PaymentRequestedEvent.VERSION,
                        "ORDER",
                        order.getId(),
                        now,
                        event.metadata().correlationId(),
                        event.metadata().eventId(),
                        "order-service");
                PaymentRequestedEvent payment = new PaymentRequestedEvent(
                        metadata, order.getId(), order.getCustomerId(),
                        order.getTotalAmount(), order.getCurrency(), deadline,
                        event.reservationId(), event.expiresAt());
                outbox.enqueue(topics.orderEvents(), order.getId(), payment, metadata);
            }
        } else if (order.getStatus() == OrderStatus.CANCEL_REQUESTED) {
            order.attachReservation(event.reservationId(), event.expiresAt(), now);
        }
        remember(event.metadata());
    }

    @Transactional
    public void committed(InventoryCommittedEvent event) {
        event.metadata().require(EventType.INVENTORY_COMMITTED, InventoryCommittedEvent.VERSION);
        if (duplicate(event.metadata())) return;
        CustomerOrder order = requireOrder(event.orderId());
        validateAggregate(event.metadata(), order);
        if (!event.reservationId().equals(order.getInventoryReservationId())) {
            throw new IllegalArgumentException("Commit does not match order reservation");
        }
        Instant now = Instant.now(clock);
        if ((order.getStatus() == OrderStatus.PAID || order.getStatus() == OrderStatus.PARTIALLY_REFUNDED)
                && order.getInventoryReleasedAt() == null
                && order.getFulfillmentStatus() == FulfillmentStatus.NOT_REQUESTED) {
            order.requestDelivery(now);
            EventMetadata metadata = new EventMetadata(java.util.UUID.randomUUID(),
                    EventType.DELIVERY_REQUESTED, DeliveryRequestedEvent.VERSION,
                    "ORDER", order.getId(), now, event.metadata().correlationId(),
                    event.metadata().eventId(), "order-service");
            outbox.enqueue(topics.deliveryEvents(), order.getId(),
                    new DeliveryRequestedEvent(metadata, order.getId(), order.getCustomerId(),
                            order.getShippingAddress().snapshot(), now), metadata);
        }
        remember(event.metadata());
    }

    @Transactional
    public void failed(InventoryReservationFailedEvent event) {
        event.metadata().require(
                EventType.INVENTORY_RESERVATION_FAILED,
                InventoryReservationFailedEvent.VERSION);
        if (duplicate(event.metadata())) {
            return;
        }
        CustomerOrder order = requireOrder(event.orderId());
        validateAggregate(event.metadata(), order);
        Instant now = Instant.now(clock);
        if (order.getStatus() == OrderStatus.INVENTORY_PENDING) {
            transition(
                    order,
                    OrderStatus.INVENTORY_REJECTED,
                    "INVENTORY_REJECTED",
                    event.metadata(),
                    now);
            metrics.counter("orderflow_order_inventory_rejections_total").increment();
        } else if (order.getStatus() == OrderStatus.CANCEL_REQUESTED) {
            transition(
                    order,
                    OrderStatus.CANCELLED,
                    "NO_INVENTORY_TO_RELEASE",
                    event.metadata(),
                    now);
        }
        remember(event.metadata());
    }

    @Transactional
    public void released(InventoryReleasedEvent event) {
        event.metadata().require(EventType.INVENTORY_RELEASED, InventoryReleasedEvent.VERSION);
        if (duplicate(event.metadata())) {
            return;
        }
        CustomerOrder order = requireOrder(event.orderId());
        validateAggregate(event.metadata(), order);
        Instant now = Instant.now(clock);
        order.inventoryReleased(event.releasedAt());
        if (order.getStatus() == OrderStatus.CANCEL_REQUESTED) {
            transition(
                    order,
                    OrderStatus.CANCELLED,
                    event.reason(),
                    event.metadata(),
                    now);
        } else if (order.getStatus() == OrderStatus.PAYMENT_FAILED) {
            // Payment failure is terminal even after inventory is released.
        } else if (order.getStatus() == OrderStatus.PAYMENT_PENDING
                && "RESERVATION_EXPIRED".equals(event.reason())) {
            transition(
                    order,
                    OrderStatus.CANCELLED,
                    "RESERVATION_EXPIRED",
                    event.metadata(),
                    now);
            enqueuePaymentCancel(order, "INVENTORY_EXPIRED", event.metadata(), now);
        } else if ((order.getStatus() == OrderStatus.PAID
                        || order.getStatus() == OrderStatus.PARTIALLY_REFUNDED)
                && ("RESERVATION_EXPIRED".equals(event.reason())
                        || "INVENTORY_COMMIT_UNAVAILABLE".equals(event.reason())
                        || "DELIVERY_FAILED_BEFORE_PICKUP".equals(event.reason()))) {
            enqueuePaymentCancel(order, "SYSTEM_COMPENSATION", event.metadata(), now);
        }
        remember(event.metadata());
    }

    private void enqueuePaymentCancel(
            CustomerOrder order, String reason, EventMetadata cause, Instant now) {
        EventMetadata metadata = new EventMetadata(
                java.util.UUID.randomUUID(),
                EventType.PAYMENT_CANCEL_REQUESTED,
                PaymentCancelRequestedEvent.VERSION,
                "ORDER",
                order.getId(),
                now,
                cause.correlationId(),
                cause.eventId(),
                "order-service");
        outbox.enqueue(
                topics.orderEvents(),
                order.getId(),
                new PaymentCancelRequestedEvent(
                        metadata, order.getId(), order.getCustomerId(),
                        order.getPaymentId(), reason, now),
                metadata);
    }

    private void transition(
            CustomerOrder order,
            OrderStatus status,
            String reason,
            EventMetadata event,
            Instant now) {
        stateMachine.transition(
                order,
                status,
                reason,
                event.eventId(),
                event.correlationId(),
                now);
        metrics.counter(
                        "orderflow_order_state_transitions_total",
                        "orderStatus",
                        status.name())
                .increment();
    }

    private CustomerOrder requireOrder(java.util.UUID orderId) {
        return orders.findByIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Event order does not exist"));
    }

    private void validateAggregate(EventMetadata event, CustomerOrder order) {
        if (!event.aggregateId().equals(order.getId())) {
            throw new IllegalArgumentException("Event aggregate does not match order");
        }
    }

    private boolean duplicate(EventMetadata event) {
        boolean duplicate = processed.existsByEventIdAndConsumerName(
                event.eventId(), INVENTORY_CONSUMER);
        if (duplicate) {
            metrics.counter(
                            "orderflow_consumer_duplicates_total",
                            "service",
                            "order-service",
                            "eventType",
                            event.eventType().name())
                    .increment();
            LOGGER.debug(
                    "Duplicate event ignored eventId={} eventType={}",
                    event.eventId(),
                    event.eventType());
        }
        return duplicate;
    }

    private void remember(EventMetadata event) {
        processed.save(new ProcessedEvent(
                event.eventId(),
                event.eventType().name(),
                event.aggregateId(),
                INVENTORY_CONSUMER,
                Instant.now(clock),
                event.correlationId()));
        metrics.counter(
                        "orderflow_consumer_processed_total",
                        "service",
                        "order-service",
                        "eventType",
                        event.eventType().name())
                .increment();
    }
}
