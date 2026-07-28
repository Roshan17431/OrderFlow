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

    private EventMetadata metadata(UUID aggregateId, EventType type, int version) {
        return new EventMetadata(
                UUID.randomUUID(), type, version, "ORDER", aggregateId,
                Instant.now(), "contract-test", null, "test");
    }
}
