package com.orderflow.inventory.messaging;

import tools.jackson.databind.ObjectMapper;
import com.orderflow.events.DeliveryCancelledEvent;
import com.orderflow.events.DeliveryPickedUpEvent;
import com.orderflow.events.DeliveryFailedEvent;
import com.orderflow.events.EventType;
import com.orderflow.inventory.service.InventoryWorkflowService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
public class DeliveryEventListener {
    private final ObjectMapper mapper;
    private final InventoryWorkflowService workflows;

    public DeliveryEventListener(ObjectMapper mapper, InventoryWorkflowService workflows) {
        this.mapper = mapper;
        this.workflows = workflows;
    }

    @KafkaListener(topics = "${app.kafka.topics.delivery-events}",
            groupId = "inventory-service-delivery-events-v1")
    public void consume(String payload, @Header("eventType") String type) throws Exception {
        switch (EventType.valueOf(type)) {
            case DELIVERY_PICKED_UP -> workflows.pickedUp(mapper.readValue(payload, DeliveryPickedUpEvent.class));
            case DELIVERY_CANCELLED -> workflows.deliveryCancelled(mapper.readValue(payload, DeliveryCancelledEvent.class));
            case DELIVERY_FAILED -> workflows.deliveryFailed(mapper.readValue(payload, DeliveryFailedEvent.class));
            case DELIVERY_REQUESTED, DELIVERY_CANCEL_REQUESTED, DELIVERY_ASSIGNED,
                    DELIVERY_OUT_FOR_DELIVERY, DELIVERY_DELIVERED -> { }
            default -> throw new IllegalArgumentException("Unsupported delivery event type");
        }
    }
}
