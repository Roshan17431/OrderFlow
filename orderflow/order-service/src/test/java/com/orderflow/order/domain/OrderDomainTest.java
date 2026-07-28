package com.orderflow.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderDomainTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void orderItemsCalculateExactBigDecimalTotalsAndRemainSnapshots() {
        CustomerOrder order = CustomerOrder.create(
                UUID.randomUUID(), UUID.randomUUID(), "USD",
                new BigDecimal("29.97"), 4, NOW, "test");
        order.addItem(
                UUID.randomUUID(), "Keyboard", 3, new BigDecimal("9.99"), 7L, NOW);

        assertThat(order.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getLineTotal()).isEqualByComparingTo("29.97");
            assertThat(item.getCurrency()).isEqualTo("USD");
            assertThat(item.getProductVersion()).isEqualTo(7L);
        });
        assertThatThrownBy(() -> order.getItems().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void stateMachineAcceptsOnlyDocumentedTransitions() {
        OrderStateMachine machine = new OrderStateMachine();
        CustomerOrder order = CustomerOrder.create(
                UUID.randomUUID(), UUID.randomUUID(), "USD",
                BigDecimal.ONE, 1, NOW, "test");
        machine.transition(
                order, OrderStatus.INVENTORY_RESERVED, "RESERVED",
                UUID.randomUUID(), "test", NOW);
        machine.transition(
                order, OrderStatus.PAYMENT_PENDING, "PAYMENT_REQUIRED",
                UUID.randomUUID(), "test", NOW);
        machine.transition(
                order, OrderStatus.CANCEL_REQUESTED, "CANCEL",
                null, "test", NOW);
        machine.transition(
                order, OrderStatus.CANCELLED, "RELEASED",
                UUID.randomUUID(), "test", NOW);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getHistory()).hasSize(5);
        assertThatThrownBy(() -> machine.transition(
                        order, OrderStatus.PAYMENT_PENDING, "ILLEGAL",
                        null, "test", NOW))
                .isInstanceOf(OrderStateConflictException.class);
    }

    @Test
    void inventoryFailureIsTerminal() {
        OrderStateMachine machine = new OrderStateMachine();
        CustomerOrder order = CustomerOrder.create(
                UUID.randomUUID(), UUID.randomUUID(), "USD",
                BigDecimal.ONE, 1, NOW, "test");
        machine.transition(
                order, OrderStatus.INVENTORY_REJECTED, "NO_STOCK",
                UUID.randomUUID(), "test", NOW);
        assertThatThrownBy(() -> machine.transition(
                        order, OrderStatus.CANCEL_REQUESTED, "CANCEL",
                        null, "test", NOW))
                .isInstanceOf(OrderStateConflictException.class);
    }
}
