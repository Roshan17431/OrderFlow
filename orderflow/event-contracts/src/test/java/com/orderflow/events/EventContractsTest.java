package com.orderflow.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EventContractsTest {
    @Test
    void deliveryContractsValidateAddressAndAggregate() {
        UUID orderId = UUID.randomUUID();
        EventMetadata metadata = new EventMetadata(UUID.randomUUID(),
                EventType.DELIVERY_REQUESTED, 1, "ORDER", orderId, Instant.now(),
                "phase6-contract", null, "order-service");
        DeliveryRequestedEvent event = new DeliveryRequestedEvent(metadata, orderId,
                UUID.randomUUID(), new ShippingAddressSnapshot("Recipient", "10 Street", null,
                "Kochi", "Kerala", "682001", "IN", null), Instant.now());
        assertThat(event.metadata().eventVersion()).isEqualTo(1);
        assertThatThrownBy(() -> new ShippingAddressSnapshot("Recipient", "Bad\nAddress", null,
                "Kochi", "Kerala", "682001", "IN", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void recordsValidateVersionsMoneyAndDefensivelyCopyLists() {
        UUID orderId = UUID.randomUUID();
        EventMetadata metadata = metadata(orderId, EventType.ORDER_CREATED, 1);
        List<OrderCreatedItem> mutable = new ArrayList<>();
        mutable.add(new OrderCreatedItem(
                UUID.randomUUID(), "Keyboard", 2, new BigDecimal("10.00"),
                new BigDecimal("20.00"), "USD", 3L));
        OrderCreatedEvent event =
                new OrderCreatedEvent(metadata, orderId, UUID.randomUUID(), "USD",
                        new BigDecimal("20.00"), mutable);
        mutable.clear();

        assertThat(event.items()).hasSize(1);
        assertThatThrownBy(() -> event.items().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> new OrderCreatedItem(
                        UUID.randomUUID(), "Bad", 2, BigDecimal.ONE,
                        BigDecimal.ONE, "USD", 1L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> metadata(orderId, EventType.ORDER_CREATED, 2)
                        .require(EventType.ORDER_CREATED, 1))
                .isInstanceOf(UnsupportedEventVersionException.class);
        EventMetadata wrongAggregate = new EventMetadata(
                UUID.randomUUID(),
                EventType.ORDER_CREATED,
                1,
                "PRODUCT",
                orderId,
                Instant.now(),
                "contract-test",
                null,
                "test");
        assertThatThrownBy(() -> new OrderCreatedEvent(
                        wrongAggregate,
                        orderId,
                        UUID.randomUUID(),
                        "USD",
                        new BigDecimal("20.00"),
                        event.items()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void paymentEventsUseOrderAggregateAndValidateMoneyDeadlinesAndRefundTotals() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Instant expiry = Instant.now().plusSeconds(900);
        PaymentRequestedEvent request = new PaymentRequestedEvent(
                metadata(orderId, EventType.PAYMENT_REQUESTED, 1),
                orderId,
                customerId,
                new BigDecimal("149.97"),
                "USD",
                expiry.minusSeconds(60),
                UUID.randomUUID(),
                expiry);
        assertThat(request.amount()).isEqualByComparingTo("149.97");
        assertThatThrownBy(() -> new PaymentRequestedEvent(
                        metadata(orderId, EventType.PAYMENT_REQUESTED, 1),
                        orderId, customerId, BigDecimal.ZERO, "USD",
                        expiry.minusSeconds(60), UUID.randomUUID(), expiry))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PaymentRequestedEvent(
                        metadata(orderId, EventType.PAYMENT_REQUESTED, 1),
                        orderId, customerId, BigDecimal.ONE, "USD",
                        expiry.plusSeconds(1), UUID.randomUUID(), expiry))
                .isInstanceOf(IllegalArgumentException.class);

        PaymentRefundedEvent full = new PaymentRefundedEvent(
                metadata(orderId, EventType.PAYMENT_REFUNDED, 1),
                UUID.randomUUID(), UUID.randomUUID(), orderId, customerId,
                new BigDecimal("149.97"), new BigDecimal("149.97"),
                new BigDecimal("149.97"), "USD", true, Instant.now());
        assertThat(full.fullRefund()).isTrue();
        assertThatThrownBy(() -> new PaymentRefundedEvent(
                        metadata(orderId, EventType.PAYMENT_REFUNDED, 1),
                        UUID.randomUUID(), UUID.randomUUID(), orderId, customerId,
                        BigDecimal.TEN, BigDecimal.TEN,
                        new BigDecimal("149.97"), "USD", true, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private EventMetadata metadata(UUID aggregateId, EventType type, int version) {
        return new EventMetadata(
                UUID.randomUUID(), type, version, "ORDER", aggregateId,
                Instant.now(), "contract-test", null, "test");
    }
}
