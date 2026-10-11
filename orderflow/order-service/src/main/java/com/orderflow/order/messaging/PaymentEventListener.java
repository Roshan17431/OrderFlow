package com.orderflow.order.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderflow.events.EventType;
import com.orderflow.events.PaymentCancelledEvent;
import com.orderflow.events.PaymentFailedEvent;
import com.orderflow.events.PaymentRefundFailedEvent;
import com.orderflow.events.PaymentRefundedEvent;
import com.orderflow.events.PaymentSucceededEvent;
import com.orderflow.order.service.OrderPaymentWorkflowService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventListener {
    private final ObjectMapper objectMapper;
    private final OrderPaymentWorkflowService workflow;

    public PaymentEventListener(
            ObjectMapper objectMapper, OrderPaymentWorkflowService workflow) {
        this.objectMapper = objectMapper;
        this.workflow = workflow;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.payment-events}",
            groupId = "order-service-payment-events-v1")
    public void consume(String payload, @Header("eventType") String eventType)
            throws JsonProcessingException {
        EventType type = EventType.valueOf(eventType);
        switch (type) {
            case PAYMENT_SUCCEEDED -> workflow.succeeded(
                    objectMapper.readValue(payload, PaymentSucceededEvent.class));
            case PAYMENT_FAILED -> workflow.failed(
                    objectMapper.readValue(payload, PaymentFailedEvent.class));
            case PAYMENT_CANCELLED -> workflow.cancelled(
                    objectMapper.readValue(payload, PaymentCancelledEvent.class));
            case PAYMENT_REFUNDED -> workflow.refunded(
                    objectMapper.readValue(payload, PaymentRefundedEvent.class));
            case PAYMENT_REFUND_FAILED -> workflow.refundFailed(
                    objectMapper.readValue(payload, PaymentRefundFailedEvent.class));
            default -> throw new IllegalArgumentException("Unsupported payment event type");
        }
    }
}
