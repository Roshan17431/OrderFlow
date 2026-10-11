package com.orderflow.order.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderflow.events.DeliveryAssignedEvent;
import com.orderflow.events.DeliveryCancelledEvent;
import com.orderflow.events.DeliveryDeliveredEvent;
import com.orderflow.events.DeliveryFailedEvent;
import com.orderflow.events.DeliveryOutForDeliveryEvent;
import com.orderflow.events.DeliveryPickedUpEvent;
import com.orderflow.events.EventType;
import com.orderflow.order.service.OrderDeliveryWorkflowService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
public class DeliveryEventListener {
    private final ObjectMapper mapper; private final OrderDeliveryWorkflowService workflow;
    public DeliveryEventListener(ObjectMapper mapper, OrderDeliveryWorkflowService workflow) { this.mapper = mapper; this.workflow = workflow; }
    @KafkaListener(topics = "${app.kafka.topics.delivery-events}", groupId = "order-service-delivery-events-v1")
    public void consume(String payload, @Header("eventType") String name) throws JsonProcessingException {
        switch (EventType.valueOf(name)) {
            case DELIVERY_ASSIGNED -> workflow.assigned(mapper.readValue(payload, DeliveryAssignedEvent.class));
            case DELIVERY_PICKED_UP -> workflow.pickedUp(mapper.readValue(payload, DeliveryPickedUpEvent.class));
            case DELIVERY_OUT_FOR_DELIVERY -> workflow.outForDelivery(mapper.readValue(payload, DeliveryOutForDeliveryEvent.class));
            case DELIVERY_DELIVERED -> workflow.delivered(mapper.readValue(payload, DeliveryDeliveredEvent.class));
            case DELIVERY_FAILED -> workflow.failed(mapper.readValue(payload, DeliveryFailedEvent.class));
            case DELIVERY_CANCELLED -> workflow.cancelled(mapper.readValue(payload, DeliveryCancelledEvent.class));
            // Commands and facts share the delivery topic. The order-service publishes
            // these commands, while delivery-service consumes them; seeing them in this
            // consumer group is expected and must not turn a valid command into a DLT.
            case DELIVERY_REQUESTED, DELIVERY_CANCEL_REQUESTED -> { }
            default -> throw new IllegalArgumentException("Unsupported delivery event type");
        }
    }
}
