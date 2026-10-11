package com.orderflow.order.service;

import com.orderflow.events.DeliveryAssignedEvent;
import com.orderflow.events.DeliveryCancelledEvent;
import com.orderflow.events.DeliveryDeliveredEvent;
import com.orderflow.events.DeliveryFailedEvent;
import com.orderflow.events.DeliveryOutForDeliveryEvent;
import com.orderflow.events.DeliveryPickedUpEvent;
import com.orderflow.events.EventMetadata;
import com.orderflow.events.EventType;
import com.orderflow.order.domain.CustomerOrder;
import com.orderflow.order.domain.ProcessedEvent;
import com.orderflow.order.repository.CustomerOrderRepository;
import com.orderflow.order.repository.ProcessedEventRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderDeliveryWorkflowService {
    static final String CONSUMER = "order-delivery-events-v1";
    private final CustomerOrderRepository orders;
    private final ProcessedEventRepository processed;
    private final Clock clock;
    public OrderDeliveryWorkflowService(CustomerOrderRepository orders,
            ProcessedEventRepository processed, Clock clock) {
        this.orders = orders; this.processed = processed; this.clock = clock;
    }

    @Transactional public void assigned(DeliveryAssignedEvent event) { apply(event.metadata(), event.orderId(), event.customerId(), o -> o.deliveryAssigned(event.deliveryId(), event.deliveryAgentId(), event.assignedAt())); }
    @Transactional public void pickedUp(DeliveryPickedUpEvent event) { apply(event.metadata(), event.orderId(), event.customerId(), o -> o.deliveryPickedUp(event.pickedUpAt())); }
    @Transactional public void outForDelivery(DeliveryOutForDeliveryEvent event) { apply(event.metadata(), event.orderId(), event.customerId(), o -> o.deliveryOutForDelivery(event.outForDeliveryAt())); }
    @Transactional public void delivered(DeliveryDeliveredEvent event) { apply(event.metadata(), event.orderId(), event.customerId(), o -> o.deliveryDelivered(event.deliveredAt())); }
    @Transactional public void failed(DeliveryFailedEvent event) { apply(event.metadata(), event.orderId(), event.customerId(), o -> o.deliveryFailed(event.failedAt())); }
    @Transactional public void cancelled(DeliveryCancelledEvent event) { apply(event.metadata(), event.orderId(), event.customerId(), o -> o.deliveryCancelled(event.cancelledAt())); }

    private void apply(EventMetadata metadata, UUID orderId, UUID customerId,
            java.util.function.Consumer<CustomerOrder> action) {
        if (processed.existsByEventIdAndConsumerName(metadata.eventId(), CONSUMER)) return;
        CustomerOrder order = orders.findByIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Delivery order does not exist"));
        if (!order.getCustomerId().equals(customerId) || !metadata.aggregateId().equals(orderId))
            throw new IllegalArgumentException("Delivery event does not match order");
        action.accept(order);
        order.recordWorkflowEvent(metadata.eventType().name(), metadata.eventId(),
                metadata.correlationId(), Instant.now(clock));
        processed.save(new ProcessedEvent(metadata.eventId(), metadata.eventType().name(), orderId,
                CONSUMER, Instant.now(clock), metadata.correlationId()));
    }
}
