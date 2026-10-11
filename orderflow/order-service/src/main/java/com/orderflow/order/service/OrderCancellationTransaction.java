package com.orderflow.order.service;

import com.orderflow.events.EventMetadata;
import com.orderflow.events.EventType;
import com.orderflow.events.OrderCancelRequestedEvent;
import com.orderflow.events.PaymentCancelRequestedEvent;
import com.orderflow.order.config.KafkaTopicProperties;
import com.orderflow.order.domain.CustomerOrder;
import com.orderflow.order.domain.IdempotencyRecord;
import com.orderflow.order.domain.IdempotencyScope;
import com.orderflow.order.domain.OrderStateConflictException;
import com.orderflow.order.domain.OrderStateMachine;
import com.orderflow.order.domain.OrderStatus;
import com.orderflow.order.dto.OrderResponse;
import com.orderflow.order.error.OrderDomainException;
import com.orderflow.order.outbox.OutboxStore;
import com.orderflow.order.repository.CustomerOrderRepository;
import com.orderflow.order.repository.IdempotencyRecordRepository;
import com.orderflow.order.security.RequestIdentity;
import com.orderflow.platform.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderCancellationTransaction {

    private final CustomerOrderRepository orders;
    private final IdempotencyRecordRepository idempotency;
    private final OrderStateMachine stateMachine;
    private final OutboxStore outbox;
    private final KafkaTopicProperties topics;
    private final OrderMapper mapper;
    private final Clock clock;

    public OrderCancellationTransaction(
            CustomerOrderRepository orders,
            IdempotencyRecordRepository idempotency,
            OrderStateMachine stateMachine,
            OutboxStore outbox,
            KafkaTopicProperties topics,
            OrderMapper mapper,
            Clock clock) {
        this.orders = orders;
        this.idempotency = idempotency;
        this.stateMachine = stateMachine;
        this.outbox = outbox;
        this.topics = topics;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional
    public OrderResponse cancel(
            RequestIdentity actor,
            UUID orderId,
            String key,
            String requestHash,
            String reason,
            String correlationId) {
        Instant now = Instant.now(clock);
        IdempotencyRecord record = new IdempotencyRecord(
                IdempotencyScope.CANCEL_ORDER,
                actor.userId(),
                key,
                requestHash,
                now);
        idempotency.saveAndFlush(record);
        CustomerOrder order = orders.findByIdForUpdate(orderId).orElseThrow(this::notFound);
        requireAccess(actor, order);
        if (order.getStatus() == OrderStatus.INVENTORY_REJECTED) {
            throw new OrderDomainException(
                    HttpStatus.CONFLICT,
                    ErrorCode.ORDER_STATE_CONFLICT,
                    "Inventory-rejected orders are already terminal.");
        }
        if (order.getStatus() != OrderStatus.CANCELLED) {
            try {
                stateMachine.transition(
                        order,
                        OrderStatus.CANCEL_REQUESTED,
                        "CANCELLATION_REQUESTED",
                        null,
                        correlationId,
                        now);
            } catch (OrderStateConflictException exception) {
                throw new OrderDomainException(
                        HttpStatus.CONFLICT,
                        ErrorCode.ORDER_STATE_CONFLICT,
                        "The order cannot be cancelled from its current state.");
            }
            order.requestCancellation(reason);
            EventMetadata metadata = new EventMetadata(
                    UUID.randomUUID(),
                    EventType.ORDER_CANCEL_REQUESTED,
                    OrderCancelRequestedEvent.VERSION,
                    "ORDER",
                    orderId,
                    now,
                    correlationId,
                    null,
                    "order-service");
            OrderCancelRequestedEvent event = new OrderCancelRequestedEvent(
                    metadata, orderId, order.getCustomerId(), reason);
            outbox.enqueue(topics.orderEvents(), orderId, event, metadata);
            EventMetadata paymentMetadata = new EventMetadata(
                    UUID.randomUUID(),
                    EventType.PAYMENT_CANCEL_REQUESTED,
                    PaymentCancelRequestedEvent.VERSION,
                    "ORDER",
                    orderId,
                    now,
                    correlationId,
                    metadata.eventId(),
                    "order-service");
            PaymentCancelRequestedEvent paymentCancellation =
                    new PaymentCancelRequestedEvent(
                            paymentMetadata,
                            orderId,
                            order.getCustomerId(),
                            order.getPaymentId(),
                            reason,
                            now);
            outbox.enqueue(
                    topics.orderEvents(), orderId, paymentCancellation, paymentMetadata);
        }
        record.complete(orderId, 202, now);
        idempotency.save(record);
        return mapper.response(order);
    }

    private void requireAccess(RequestIdentity actor, CustomerOrder order) {
        if (!actor.admin() && !actor.userId().equals(order.getCustomerId())) {
            throw notFound();
        }
    }

    private OrderDomainException notFound() {
        return new OrderDomainException(
                HttpStatus.NOT_FOUND, ErrorCode.ORDER_NOT_FOUND, "Order was not found.");
    }
}
