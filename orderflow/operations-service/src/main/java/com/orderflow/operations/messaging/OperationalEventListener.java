package com.orderflow.operations.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderflow.events.DeliveryAssignedEvent;
import com.orderflow.events.DeliveryDeliveredEvent;
import com.orderflow.events.DeliveryFailedEvent;
import com.orderflow.events.EventType;
import com.orderflow.events.InventoryReservationFailedEvent;
import com.orderflow.events.InventoryReservedEvent;
import com.orderflow.events.NotificationDeliveredEvent;
import com.orderflow.events.NotificationFailedEvent;
import com.orderflow.events.OrderCreatedEvent;
import com.orderflow.events.PaymentFailedEvent;
import com.orderflow.events.PaymentRefundedEvent;
import com.orderflow.events.PaymentSucceededEvent;
import com.orderflow.operations.service.OperationalReadModelService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
public class OperationalEventListener {
    private final ObjectMapper mapper;
    private final OperationalReadModelService readModel;

    public OperationalEventListener(ObjectMapper mapper, OperationalReadModelService readModel) {
        this.mapper = mapper;
        this.readModel = readModel;
    }

    @KafkaListener(topics = "${app.kafka.topics.order-events}", groupId = "operations-order-read-model")
    void order(String payload, @Header("eventType") String type) throws Exception {
        if (EventType.valueOf(type) == EventType.ORDER_CREATED) {
            OrderCreatedEvent event = mapper.readValue(payload, OrderCreatedEvent.class);
            event.metadata().require(EventType.ORDER_CREATED, OrderCreatedEvent.VERSION);
            readModel.amount(event.metadata(), "ORDERS_CREATED", "ORDER_VALUE",
                    "currency=" + event.currency(), event.currency(), event.totalAmount());
        }
    }

    @KafkaListener(topics = "${app.kafka.topics.inventory-events}", groupId = "operations-inventory-read-model")
    void inventory(String payload, @Header("eventType") String type) throws Exception {
        switch (EventType.valueOf(type)) {
            case INVENTORY_RESERVED -> {
                InventoryReservedEvent event = mapper.readValue(payload, InventoryReservedEvent.class);
                event.metadata().require(EventType.INVENTORY_RESERVED, InventoryReservedEvent.VERSION);
                readModel.count(event.metadata(), "INVENTORY_RESERVATION_SUCCEEDED", "all");
            }
            case INVENTORY_RESERVATION_FAILED -> {
                InventoryReservationFailedEvent event = mapper.readValue(payload, InventoryReservationFailedEvent.class);
                event.metadata().require(EventType.INVENTORY_RESERVATION_FAILED, InventoryReservationFailedEvent.VERSION);
                String reason = event.failures().getFirst().reasonCode();
                readModel.failure(event.metadata(), "INVENTORY_RESERVATION_FAILED", "inventory-service",
                        "INVENTORY_RESERVATION_FAILED", reason, "failureCategory=" + reason, "WARNING");
            }
            default -> { }
        }
    }

    @KafkaListener(topics = "${app.kafka.topics.payment-events}", groupId = "operations-payment-read-model")
    void payment(String payload, @Header("eventType") String type) throws Exception {
        switch (EventType.valueOf(type)) {
            case PAYMENT_SUCCEEDED -> {
                PaymentSucceededEvent event = mapper.readValue(payload, PaymentSucceededEvent.class);
                event.metadata().require(EventType.PAYMENT_SUCCEEDED, PaymentSucceededEvent.VERSION);
                readModel.amount(event.metadata(), "PAYMENT_SUCCEEDED", "PAYMENT_VOLUME",
                        "provider=" + event.provider(), event.currency(), event.amount());
            }
            case PAYMENT_FAILED -> {
                PaymentFailedEvent event = mapper.readValue(payload, PaymentFailedEvent.class);
                event.metadata().require(EventType.PAYMENT_FAILED, PaymentFailedEvent.VERSION);
                readModel.failure(event.metadata(), "PAYMENT_FAILED", "payment-service",
                        "PAYMENT_FAILED", event.failureCode(),
                        "failureCategory=" + event.failureCategory(), "WARNING");
            }
            case PAYMENT_REFUNDED -> {
                PaymentRefundedEvent event = mapper.readValue(payload, PaymentRefundedEvent.class);
                event.metadata().require(EventType.PAYMENT_REFUNDED, PaymentRefundedEvent.VERSION);
                readModel.amount(event.metadata(), "REFUNDS_SUCCEEDED", "REFUND_VOLUME",
                        "all", event.currency(), event.refundedAmount());
            }
            default -> { }
        }
    }

    @KafkaListener(topics = "${app.kafka.topics.delivery-events}", groupId = "operations-delivery-read-model")
    void delivery(String payload, @Header("eventType") String type) throws Exception {
        switch (EventType.valueOf(type)) {
            case DELIVERY_ASSIGNED -> {
                DeliveryAssignedEvent event = mapper.readValue(payload, DeliveryAssignedEvent.class);
                event.metadata().require(EventType.DELIVERY_ASSIGNED, DeliveryAssignedEvent.VERSION);
                readModel.count(event.metadata(), "DELIVERY_ASSIGNED", "all");
            }
            case DELIVERY_DELIVERED -> {
                DeliveryDeliveredEvent event = mapper.readValue(payload, DeliveryDeliveredEvent.class);
                event.metadata().require(EventType.DELIVERY_DELIVERED, DeliveryDeliveredEvent.VERSION);
                readModel.count(event.metadata(), "DELIVERY_COMPLETED", "all");
            }
            case DELIVERY_FAILED -> {
                DeliveryFailedEvent event = mapper.readValue(payload, DeliveryFailedEvent.class);
                event.metadata().require(EventType.DELIVERY_FAILED, DeliveryFailedEvent.VERSION);
                readModel.failure(event.metadata(), "DELIVERY_FAILED", "delivery-service",
                        "DELIVERY_FAILED", event.failureCode(),
                        "failureCategory=" + event.failureCode(), "WARNING");
            }
            default -> { }
        }
    }

    @KafkaListener(topics = "${app.kafka.topics.notification-events}", groupId = "operations-notification-read-model")
    void notification(String payload, @Header("eventType") String type) throws Exception {
        switch (EventType.valueOf(type)) {
            case NOTIFICATION_DELIVERED -> {
                NotificationDeliveredEvent event = mapper.readValue(payload, NotificationDeliveredEvent.class);
                event.metadata().require(EventType.NOTIFICATION_DELIVERED, NotificationDeliveredEvent.VERSION);
                readModel.count(event.metadata(), "NOTIFICATION_SENT", "channel=" + event.channel());
            }
            case NOTIFICATION_FAILED -> {
                NotificationFailedEvent event = mapper.readValue(payload, NotificationFailedEvent.class);
                event.metadata().require(EventType.NOTIFICATION_FAILED, NotificationFailedEvent.VERSION);
                readModel.failure(event.metadata(), "NOTIFICATION_FAILED", "notification-service",
                        "NOTIFICATION_FAILED", event.failureCode(),
                        "channel=" + event.channel(), "WARNING");
            }
            default -> { }
        }
    }
}
