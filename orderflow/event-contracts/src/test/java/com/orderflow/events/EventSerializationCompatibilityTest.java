package com.orderflow.events;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EventSerializationCompatibilityTest {

    private static final Instant NOW = Instant.parse("2026-01-02T03:04:05.123456Z");
    private static final UUID ORDER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID CUSTOMER_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID PRODUCT_ID = UUID.fromString("30000000-0000-0000-0000-000000000003");
    private static final UUID RESERVATION_ID = UUID.fromString("40000000-0000-0000-0000-000000000004");
    private static final UUID PAYMENT_ID = UUID.fromString("50000000-0000-0000-0000-000000000005");
    private static final UUID REFUND_ID = UUID.fromString("60000000-0000-0000-0000-000000000006");
    private static final UUID DELIVERY_ID = UUID.fromString("70000000-0000-0000-0000-000000000007");
    private static final UUID AGENT_ID = UUID.fromString("80000000-0000-0000-0000-000000000008");
    private static final UUID NOTIFICATION_ID = UUID.fromString("90000000-0000-0000-0000-000000000009");

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    @Test
    void everyPublishedEventRoundTripsAndToleratesAdditiveFields() throws Exception {
        List<EventCase> cases = eventCases();

        assertThat(cases).extracting(eventCase -> eventCase.metadata().eventType())
                .containsExactlyInAnyOrderElementsOf(EnumSet.allOf(EventType.class));

        for (EventCase eventCase : cases) {
            JsonNode json = mapper.valueToTree(eventCase.value());
            assertThat(json.at("/metadata/eventType").asText())
                    .isEqualTo(eventCase.metadata().eventType().name());
            assertThat(json.at("/metadata/eventVersion").asInt()).isEqualTo(1);
            assertThat(json.at("/metadata/eventId").asText()).isNotBlank();
            assertThat(json.at("/metadata/occurredAt").asText()).isEqualTo(NOW.toString());

            ObjectNode withFutureOptionalField = (ObjectNode) json.deepCopy();
            withFutureOptionalField.put("futureOptionalField", "ignored-by-v1-consumer");
            Object restored = mapper.treeToValue(withFutureOptionalField, eventCase.type());
            assertThat(restored).isEqualTo(eventCase.value());
        }
    }

    @Test
    void monetaryValuesRetainDecimalPrecisionInJson() throws Exception {
        OrderCreatedEvent order = (OrderCreatedEvent) eventCases().get(0).value();
        String json = mapper.writeValueAsString(order);
        OrderCreatedEvent restored = mapper.readValue(json, OrderCreatedEvent.class);

        assertThat(mapper.readTree(json).path("totalAmount").decimalValue())
                .isEqualByComparingTo("123456789.123456");
        assertThat(restored.totalAmount()).isEqualByComparingTo(order.totalAmount());
        assertThat(restored.items().getFirst().unitPrice()).isEqualByComparingTo("41152263.041152");
    }

    private List<EventCase> eventCases() {
        Instant expiry = NOW.plusSeconds(900);
        ShippingAddressSnapshot address = new ShippingAddressSnapshot(
                "Customer", "10 Main Street", null, "Kochi", "Kerala", "682001", "IN", null);
        OrderCreatedItem orderItem = new OrderCreatedItem(
                PRODUCT_ID, "Precision item", 3, new BigDecimal("41152263.041152"),
                new BigDecimal("123456789.123456"), "USD", 7L);

        return List.of(
                c(new OrderCreatedEvent(m(EventType.ORDER_CREATED), ORDER_ID, CUSTOMER_ID, "USD",
                        new BigDecimal("123456789.123456"), List.of(orderItem))),
                c(new OrderCancelRequestedEvent(m(EventType.ORDER_CANCEL_REQUESTED), ORDER_ID,
                        CUSTOMER_ID, "CUSTOMER_REQUEST")),
                c(new InventoryReservedEvent(m(EventType.INVENTORY_RESERVED), ORDER_ID,
                        RESERVATION_ID, expiry, List.of(new InventoryReservedItem(PRODUCT_ID, 3)))),
                c(new InventoryReservationFailedEvent(m(EventType.INVENTORY_RESERVATION_FAILED),
                        ORDER_ID, List.of(new InventoryReservationFailure(PRODUCT_ID, 3, 1, "INSUFFICIENT_STOCK")))),
                c(new InventoryReleasedEvent(m(EventType.INVENTORY_RELEASED), ORDER_ID,
                        RESERVATION_ID, NOW, "ORDER_CANCELLED", true)),
                c(new InventoryCommitRequestedEvent(m(EventType.INVENTORY_COMMIT_REQUESTED), ORDER_ID,
                        RESERVATION_ID, NOW)),
                c(new InventoryCommittedEvent(m(EventType.INVENTORY_COMMITTED), ORDER_ID,
                        RESERVATION_ID, NOW)),
                c(new PaymentRequestedEvent(m(EventType.PAYMENT_REQUESTED), ORDER_ID, CUSTOMER_ID,
                        new BigDecimal("123456789.123456"), "USD", expiry.minusSeconds(30),
                        RESERVATION_ID, expiry)),
                c(new PaymentCancelRequestedEvent(m(EventType.PAYMENT_CANCEL_REQUESTED), ORDER_ID,
                        CUSTOMER_ID, PAYMENT_ID, "ORDER_CANCELLED", NOW)),
                c(new PaymentSucceededEvent(m(EventType.PAYMENT_SUCCEEDED), PAYMENT_ID, ORDER_ID,
                        CUSTOMER_ID, "TEST_PROVIDER", "provider-payment-1",
                        new BigDecimal("123456789.123456"), "USD", NOW)),
                c(new PaymentFailedEvent(m(EventType.PAYMENT_FAILED), PAYMENT_ID, ORDER_ID,
                        CUSTOMER_ID, "TEST_PROVIDER", "DECLINED", "CUSTOMER", false, NOW)),
                c(new PaymentCancelledEvent(m(EventType.PAYMENT_CANCELLED), PAYMENT_ID, ORDER_ID,
                        CUSTOMER_ID, "TEST_PROVIDER", "ORDER_CANCELLED", NOW)),
                c(new PaymentRefundedEvent(m(EventType.PAYMENT_REFUNDED), PAYMENT_ID, REFUND_ID,
                        ORDER_ID, CUSTOMER_ID, new BigDecimal("3.141592"),
                        new BigDecimal("3.141592"), new BigDecimal("123456789.123456"),
                        "USD", false, NOW)),
                c(new PaymentRefundFailedEvent(m(EventType.PAYMENT_REFUND_FAILED), PAYMENT_ID,
                        REFUND_ID, ORDER_ID, CUSTOMER_ID, new BigDecimal("3.141592"), "USD",
                        "PROVIDER_UNAVAILABLE", true, NOW)),
                c(new DeliveryRequestedEvent(m(EventType.DELIVERY_REQUESTED), ORDER_ID,
                        CUSTOMER_ID, address, NOW)),
                c(new DeliveryAssignedEvent(m(EventType.DELIVERY_ASSIGNED), DELIVERY_ID, ORDER_ID,
                        CUSTOMER_ID, AGENT_ID, NOW)),
                c(new DeliveryPickedUpEvent(m(EventType.DELIVERY_PICKED_UP), DELIVERY_ID, ORDER_ID,
                        CUSTOMER_ID, AGENT_ID, NOW)),
                c(new DeliveryOutForDeliveryEvent(m(EventType.DELIVERY_OUT_FOR_DELIVERY), DELIVERY_ID,
                        ORDER_ID, CUSTOMER_ID, AGENT_ID, NOW)),
                c(new DeliveryDeliveredEvent(m(EventType.DELIVERY_DELIVERED), DELIVERY_ID, ORDER_ID,
                        CUSTOMER_ID, AGENT_ID, NOW)),
                c(new DeliveryFailedEvent(m(EventType.DELIVERY_FAILED), DELIVERY_ID, ORDER_ID,
                        CUSTOMER_ID, AGENT_ID, "VEHICLE_PROBLEM", true, NOW)),
                c(new DeliveryCancelRequestedEvent(m(EventType.DELIVERY_CANCEL_REQUESTED), ORDER_ID,
                        CUSTOMER_ID, DELIVERY_ID, "ORDER_CANCELLED", NOW)),
                c(new DeliveryCancelledEvent(m(EventType.DELIVERY_CANCELLED), DELIVERY_ID, ORDER_ID,
                        CUSTOMER_ID, AGENT_ID, "ORDER_CANCELLED", NOW)),
                c(new NotificationDispatchRequestedEvent(m(EventType.NOTIFICATION_DISPATCH_REQUESTED),
                        NOTIFICATION_ID, CUSTOMER_ID, "EMAIL", DELIVERY_ID)),
                c(new NotificationDeliveredEvent(m(EventType.NOTIFICATION_DELIVERED), NOTIFICATION_ID,
                        CUSTOMER_ID, "EMAIL", DELIVERY_ID, NOW)),
                c(new NotificationFailedEvent(m(EventType.NOTIFICATION_FAILED), NOTIFICATION_ID,
                        CUSTOMER_ID, "EMAIL", DELIVERY_ID, "SMTP_UNAVAILABLE", NOW)));
    }

    private EventMetadata m(EventType type) {
        return new EventMetadata(UUID.nameUUIDFromBytes(type.name().getBytes()), type, 1,
                "ORDER", ORDER_ID, NOW, "contract-correlation", null, "contract-test");
    }

    private EventCase c(Object value) {
        return new EventCase(value, value.getClass());
    }

    private record EventCase(Object value, Class<?> type) {
        EventMetadata metadata() {
            try {
                return (EventMetadata) type.getMethod("metadata").invoke(value);
            } catch (ReflectiveOperationException exception) {
                throw new AssertionError(exception);
            }
        }
    }
}
