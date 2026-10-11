package com.orderflow.order.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderflow.events.EventType;
import com.orderflow.events.InventoryReleasedEvent;
import com.orderflow.events.InventoryReservationFailedEvent;
import com.orderflow.events.InventoryReservedEvent;
import com.orderflow.events.InventoryCommittedEvent;
import com.orderflow.order.service.OrderInventoryWorkflowService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
public class InventoryEventListener {

    private final ObjectMapper objectMapper;
    private final OrderInventoryWorkflowService workflows;

    public InventoryEventListener(
            ObjectMapper objectMapper, OrderInventoryWorkflowService workflows) {
        this.objectMapper = objectMapper;
        this.workflows = workflows;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.inventory-events}",
            groupId = "${spring.kafka.consumer.group-id}")
    public void consume(String payload, @Header("eventType") String eventType)
            throws JsonProcessingException {
        EventType type = EventType.valueOf(eventType);
        switch (type) {
            case INVENTORY_RESERVED -> workflows.reserved(
                    objectMapper.readValue(payload, InventoryReservedEvent.class));
            case INVENTORY_RESERVATION_FAILED -> workflows.failed(
                    objectMapper.readValue(payload, InventoryReservationFailedEvent.class));
            case INVENTORY_RELEASED -> workflows.released(
                    objectMapper.readValue(payload, InventoryReleasedEvent.class));
            case INVENTORY_COMMITTED -> workflows.committed(
                    objectMapper.readValue(payload, InventoryCommittedEvent.class));
            default -> throw new IllegalArgumentException("Unsupported inventory event type");
        }
    }
}
