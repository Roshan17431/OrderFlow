package com.orderflow.payment.messaging;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.orderflow.events.EventType;
import com.orderflow.events.PaymentCancelRequestedEvent;
import com.orderflow.events.PaymentRequestedEvent;
import com.orderflow.payment.service.PaymentOrderWorkflowService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
public class OrderEventListener {
    private final ObjectMapper objectMapper;
    private final PaymentOrderWorkflowService workflow;

    public OrderEventListener(
            ObjectMapper objectMapper, PaymentOrderWorkflowService workflow) {
        this.objectMapper = objectMapper;
        this.workflow = workflow;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.order-events}",
            groupId = "${spring.kafka.consumer.group-id}")
    public void consume(String payload, @Header("eventType") String eventType)
            throws JacksonException {
        EventType type = EventType.valueOf(eventType);
        switch (type) {
            case PAYMENT_REQUESTED ->
                    workflow.create(objectMapper.readValue(payload, PaymentRequestedEvent.class));
            case PAYMENT_CANCEL_REQUESTED -> workflow.cancel(
                    objectMapper.readValue(payload, PaymentCancelRequestedEvent.class));
            case ORDER_CREATED, ORDER_CANCEL_REQUESTED, INVENTORY_COMMIT_REQUESTED -> {
                // These order-topic event types belong to the inventory consumer.
            }
            default -> throw new IllegalArgumentException("Unsupported payment order event type");
        }
    }
}
