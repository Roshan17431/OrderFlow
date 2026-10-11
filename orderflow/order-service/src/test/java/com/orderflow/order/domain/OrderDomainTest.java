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

    @Test
    void paymentAndRefundLifecycleIsMonotonic() {
        OrderStateMachine machine = new OrderStateMachine();
        CustomerOrder order = CustomerOrder.create(
                UUID.randomUUID(), UUID.randomUUID(), "USD",
                new BigDecimal("100.00"), 1, NOW, "test");
        machine.transition(
                order, OrderStatus.INVENTORY_RESERVED, "RESERVED",
                UUID.randomUUID(), "test", NOW);
        machine.transition(
                order, OrderStatus.PAYMENT_PENDING, "PAYMENT_REQUIRED",
                UUID.randomUUID(), "test", NOW);
        UUID paymentId = UUID.randomUUID();
        order.paymentSucceeded(paymentId, NOW.plusSeconds(1));
        machine.transition(
                order, OrderStatus.PAID, "PAYMENT_SUCCEEDED",
                UUID.randomUUID(), "test", NOW.plusSeconds(1));
        order.paymentRefunded(new BigDecimal("25.00"), NOW.plusSeconds(2));
        machine.transition(
                order, OrderStatus.PARTIALLY_REFUNDED, "PARTIAL_REFUND",
                UUID.randomUUID(), "test", NOW.plusSeconds(2));
        order.paymentRefunded(new BigDecimal("100.00"), NOW.plusSeconds(3));
        machine.transition(
                order, OrderStatus.REFUNDED, "FULL_REFUND",
                UUID.randomUUID(), "test", NOW.plusSeconds(3));

        assertThat(order.getPaymentId()).isEqualTo(paymentId);
        assertThat(order.getRefundedAmount()).isEqualByComparingTo("100.00");
        assertThatThrownBy(() -> machine.transition(
                        order, OrderStatus.PAID, "ILLEGAL",
                        null, "test", NOW.plusSeconds(4)))
                .isInstanceOf(OrderStateConflictException.class);
    }

    @Test
    void paymentFailureRemainsTerminalAfterInventoryReleaseTimestamp() {
        OrderStateMachine machine = new OrderStateMachine();
        CustomerOrder order = CustomerOrder.create(
                UUID.randomUUID(), UUID.randomUUID(), "USD",
                BigDecimal.TEN, 1, NOW, "test");
        machine.transition(order, OrderStatus.INVENTORY_RESERVED, "RESERVED",
                null, "test", NOW);
        machine.transition(order, OrderStatus.PAYMENT_PENDING, "PAYMENT",
                null, "test", NOW);
        order.paymentFailed(UUID.randomUUID(), "DECLINED", NOW.plusSeconds(1));
        machine.transition(order, OrderStatus.PAYMENT_FAILED, "FAILED",
                null, "test", NOW.plusSeconds(1));
        order.inventoryReleased(NOW.plusSeconds(2));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
        assertThat(order.getInventoryReleasedAt()).isEqualTo(NOW.plusSeconds(2));
    }

    @Test
    void shippingSnapshotAndFulfillmentLifecycleRemainSeparateFromPayment() {
        ShippingAddress address = new ShippingAddress(new com.orderflow.events.ShippingAddressSnapshot(
                "Recipient", "10 Main Street", "Apartment 2", "Kochi", "Kerala",
                "682001", "IN", "Leave at reception"));
        CustomerOrder order = CustomerOrder.create(UUID.randomUUID(), UUID.randomUUID(), "USD",
                new BigDecimal("49.99"), 3, address, NOW, "phase6-test");
        order.paymentSucceeded(UUID.randomUUID(), NOW.plusSeconds(1));
        OrderStateMachine machine = new OrderStateMachine();
        machine.transition(order, OrderStatus.INVENTORY_RESERVED, "RESERVED", null,
                "phase6-test", NOW);
        machine.transition(order, OrderStatus.PAYMENT_PENDING, "PAYMENT", null,
                "phase6-test", NOW);
        machine.transition(order, OrderStatus.PAID, "PAID", null,
                "phase6-test", NOW.plusSeconds(1));

        UUID delivery = UUID.randomUUID();
        order.requestDelivery(NOW.plusSeconds(2));
        order.requestDelivery(NOW.plusSeconds(2));
        order.deliveryAssigned(delivery, UUID.randomUUID(), NOW.plusSeconds(3));
        order.deliveryPickedUp(NOW.plusSeconds(4));
        order.deliveryOutForDelivery(NOW.plusSeconds(5));
        order.deliveryDelivered(NOW.plusSeconds(6));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getFulfillmentStatus()).isEqualTo(FulfillmentStatus.DELIVERED);
        assertThat(order.getDeliveryId()).isEqualTo(delivery);
        assertThat(order.getShippingAddress().snapshot()).isEqualTo(address.snapshot());
        assertThatThrownBy(() -> order.requestDeliveryCancellation(NOW.plusSeconds(7)))
                .isInstanceOf(IllegalStateException.class);
    }
}
