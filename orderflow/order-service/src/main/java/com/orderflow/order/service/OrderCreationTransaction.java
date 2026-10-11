package com.orderflow.order.service;

import com.orderflow.events.EventMetadata;
import com.orderflow.events.EventType;
import com.orderflow.events.OrderCreatedEvent;
import com.orderflow.events.ShippingAddressSnapshot;
import com.orderflow.order.client.CartSnapshot;
import com.orderflow.order.config.KafkaTopicProperties;
import com.orderflow.order.domain.CustomerOrder;
import com.orderflow.order.domain.IdempotencyRecord;
import com.orderflow.order.domain.ShippingAddress;
import com.orderflow.order.domain.IdempotencyScope;
import com.orderflow.order.dto.OrderResponse;
import com.orderflow.order.outbox.OutboxStore;
import com.orderflow.order.repository.CustomerOrderRepository;
import com.orderflow.order.repository.IdempotencyRecordRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderCreationTransaction {

    private final CustomerOrderRepository orders;
    private final IdempotencyRecordRepository idempotency;
    private final OutboxStore outbox;
    private final KafkaTopicProperties topics;
    private final OrderMapper mapper;
    private final Clock clock;

    public OrderCreationTransaction(
            CustomerOrderRepository orders,
            IdempotencyRecordRepository idempotency,
            OutboxStore outbox,
            KafkaTopicProperties topics,
            OrderMapper mapper,
            Clock clock) {
        this.orders = orders;
        this.idempotency = idempotency;
        this.outbox = outbox;
        this.topics = topics;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional
    public OrderResponse create(
            UUID customerId,
            String key,
            String requestHash,
            CartSnapshot cart,
            ShippingAddressSnapshot shippingAddress,
            String correlationId) {
        Instant now = Instant.now(clock);
        IdempotencyRecord record = new IdempotencyRecord(
                IdempotencyScope.CREATE_ORDER, customerId, key, requestHash, now);
        idempotency.saveAndFlush(record);

        BigDecimal total = cart.items().stream()
                .map(item -> item.unitPriceSnapshot()
                        .multiply(BigDecimal.valueOf(item.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        UUID orderId = UUID.randomUUID();
        CustomerOrder order = CustomerOrder.create(
                orderId, customerId, cart.currency(), total, cart.version(),
                new ShippingAddress(shippingAddress), now, correlationId);
        for (var item : cart.items()) {
            order.addItem(
                    item.productId(),
                    item.productNameSnapshot(),
                    item.quantity(),
                    item.unitPriceSnapshot(),
                    item.productVersionSnapshot(),
                    now);
        }
        orders.save(order);

        EventMetadata metadata = new EventMetadata(
                UUID.randomUUID(),
                EventType.ORDER_CREATED,
                OrderCreatedEvent.VERSION,
                "ORDER",
                orderId,
                now,
                correlationId,
                null,
                "order-service");
        OrderCreatedEvent event = new OrderCreatedEvent(
                metadata,
                orderId,
                customerId,
                order.getCurrency(),
                order.getTotalAmount(),
                order.getItems().stream().map(mapper::eventItem).toList());
        outbox.enqueue(topics.orderEvents(), orderId, event, metadata);
        record.complete(orderId, 202, now);
        idempotency.save(record);
        return mapper.response(order);
    }
}
