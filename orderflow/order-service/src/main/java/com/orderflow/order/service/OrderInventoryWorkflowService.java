package com.orderflow.order.service;

import com.orderflow.events.EventMetadata;
import com.orderflow.events.EventType;
import com.orderflow.events.InventoryReleasedEvent;
import com.orderflow.events.InventoryReservationFailedEvent;
import com.orderflow.events.InventoryReservedEvent;
import com.orderflow.order.domain.CustomerOrder;
import com.orderflow.order.domain.OrderStateMachine;
import com.orderflow.order.domain.OrderStatus;
import com.orderflow.order.domain.ProcessedEvent;
import com.orderflow.order.repository.CustomerOrderRepository;
import com.orderflow.order.repository.ProcessedEventRepository;
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
    private final Clock clock;

    public OrderInventoryWorkflowService(
            CustomerOrderRepository orders,
            ProcessedEventRepository processed,
            OrderStateMachine stateMachine,
            MeterRegistry metrics,
            Clock clock) {
        this.orders = orders;
        this.processed = processed;
        this.stateMachine = stateMachine;
        this.metrics = metrics;
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
        } else if (order.getStatus() == OrderStatus.CANCEL_REQUESTED) {
            order.attachReservation(event.reservationId(), event.expiresAt(), now);
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
        if (order.getStatus() == OrderStatus.CANCEL_REQUESTED) {
            transition(
                    order,
                    OrderStatus.CANCELLED,
                    event.reason(),
                    event.metadata(),
                    now);
        } else if (order.getStatus() == OrderStatus.PAYMENT_PENDING
                && "RESERVATION_EXPIRED".equals(event.reason())) {
            transition(
                    order,
                    OrderStatus.CANCEL_REQUESTED,
                    "RESERVATION_EXPIRED",
                    event.metadata(),
                    now);
            transition(
                    order,
                    OrderStatus.CANCELLED,
                    "RESERVATION_EXPIRED",
                    event.metadata(),
                    now);
        }
        remember(event.metadata());
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
