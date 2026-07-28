package com.orderflow.inventory.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderflow.events.EventType;
import com.orderflow.events.OrderCancelRequestedEvent;
import com.orderflow.events.OrderCreatedEvent;
import com.orderflow.inventory.service.InventoryWorkflowService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
public class OrderEventListener {

    private final ObjectMapper objectMapper;
    private final InventoryWorkflowService workflows;

    public OrderEventListener(ObjectMapper objectMapper, InventoryWorkflowService workflows) {
        this.objectMapper = objectMapper;
        this.workflows = workflows;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.order-events}",
            groupId = "${spring.kafka.consumer.group-id}")
    public void consume(String payload, @Header("eventType") String eventType)
            throws JsonProcessingException {
        EventType type = EventType.valueOf(eventType);
        switch (type) {
            case ORDER_CREATED ->
                    workflows.reserve(objectMapper.readValue(payload, OrderCreatedEvent.class));
            case ORDER_CANCEL_REQUESTED -> workflows.release(
                    objectMapper.readValue(payload, OrderCancelRequestedEvent.class));
            default -> throw new IllegalArgumentException("Unsupported order event type");
        }
    }
}
