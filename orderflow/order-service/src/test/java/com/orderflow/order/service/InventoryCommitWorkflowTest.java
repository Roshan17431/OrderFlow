package com.orderflow.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.orderflow.events.*;
import com.orderflow.order.config.*;
import com.orderflow.order.domain.*;
import com.orderflow.order.outbox.OutboxStore;
import com.orderflow.order.repository.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InventoryCommitWorkflowTest {
    private final Instant now = Instant.parse("2026-10-11T00:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private final CustomerOrderRepository orders = mock(CustomerOrderRepository.class);
    private final ProcessedEventRepository processed = mock(ProcessedEventRepository.class);
    private final OutboxStore outbox = mock(OutboxStore.class);
    private final OrderStateMachine states = new OrderStateMachine();
    private final SimpleMeterRegistry metrics = new SimpleMeterRegistry();
    private final KafkaTopicProperties topics = new KafkaTopicProperties("order", "inventory", "payment", "delivery", "od", "id", "pd", "dd");
    private final OrderProperties properties = new OrderProperties(100, Duration.ofMinutes(10), Duration.ofSeconds(30));
    private final OrderPaymentWorkflowService payments = new OrderPaymentWorkflowService(orders, processed, states, outbox, topics, metrics, clock);
    private final OrderInventoryWorkflowService inventory = new OrderInventoryWorkflowService(orders, processed, states, metrics, outbox, topics, properties, clock);
    private CustomerOrder order;

    @BeforeEach void order() {
        order = CustomerOrder.create(UUID.randomUUID(), UUID.randomUUID(), "USD", BigDecimal.TEN, 1, now, "commit-test");
        order.attachReservation(UUID.randomUUID(), now.plusSeconds(600), now);
        states.transition(order, OrderStatus.INVENTORY_RESERVED, "reserved", null, "test", now);
        states.transition(order, OrderStatus.PAYMENT_PENDING, "pending", null, "test", now);
        when(orders.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
    }

    @Test void deliveryWaitsForInventoryCommitAndAckCannotCreateTwoDeliveries() {
        payments.succeeded(success());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getFulfillmentStatus()).isEqualTo(FulfillmentStatus.NOT_REQUESTED);
        verify(outbox).enqueue(eq("order"), eq(order.getId()), isA(InventoryCommitRequestedEvent.class), any());
        verify(outbox, never()).enqueue(eq("delivery"), any(), any(), any());
        inventory.committed(committed());
        inventory.committed(committed());
        assertThat(order.getFulfillmentStatus()).isEqualTo(FulfillmentStatus.DELIVERY_PENDING);
        verify(outbox, times(1)).enqueue(eq("delivery"), eq(order.getId()), isA(DeliveryRequestedEvent.class), any());
    }

    @Test void fullRefundBeforeCommitAckReleasesStockAndNeverStartsDelivery() {
        payments.succeeded(success());
        payments.refunded(new PaymentRefundedEvent(metadata(EventType.PAYMENT_REFUNDED), order.getPaymentId(), UUID.randomUUID(), order.getId(), order.getCustomerId(), BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, "USD", true, now));
        inventory.committed(committed());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
        verify(outbox).enqueue(eq("order"), eq(order.getId()), isA(OrderCancelRequestedEvent.class), any());
        verify(outbox, never()).enqueue(eq("delivery"), any(), any(), any());
    }

    @Test void expiryAfterPaymentTriggersCompensationAndLateAckCannotStartDelivery() {
        payments.succeeded(success());
        inventory.released(new InventoryReleasedEvent(metadata(EventType.INVENTORY_RELEASED), order.getId(), order.getInventoryReservationId(), now, "INVENTORY_COMMIT_UNAVAILABLE", true));
        inventory.committed(committed());
        verify(outbox).enqueue(eq("order"), eq(order.getId()), isA(PaymentCancelRequestedEvent.class), any());
        verify(outbox, never()).enqueue(eq("delivery"), any(), any(), any());
    }

    @Test void cancellationReconciliationAndFreshDuplicateEventsCannotRegressDelivery() {
        payments.succeeded(success());
        inventory.committed(committed());
        var workflow = new OrderDeliveryWorkflowService(orders, processed, clock);
        UUID deliveryId = UUID.randomUUID(), agentId = UUID.randomUUID();
        workflow.assigned(new DeliveryAssignedEvent(metadata(EventType.DELIVERY_ASSIGNED), deliveryId, order.getId(), order.getCustomerId(), agentId, now));
        workflow.pickedUp(new DeliveryPickedUpEvent(metadata(EventType.DELIVERY_PICKED_UP), deliveryId, order.getId(), order.getCustomerId(), agentId, now));
        order.requestDeliveryCancellation(now);
        workflow.outForDelivery(new DeliveryOutForDeliveryEvent(metadata(EventType.DELIVERY_OUT_FOR_DELIVERY), deliveryId, order.getId(), order.getCustomerId(), agentId, now));
        workflow.outForDelivery(new DeliveryOutForDeliveryEvent(metadata(EventType.DELIVERY_OUT_FOR_DELIVERY), deliveryId, order.getId(), order.getCustomerId(), agentId, now));
        workflow.assigned(new DeliveryAssignedEvent(metadata(EventType.DELIVERY_ASSIGNED), deliveryId, order.getId(), order.getCustomerId(), agentId, now));
        assertThat(order.getFulfillmentStatus()).isEqualTo(FulfillmentStatus.OUT_FOR_DELIVERY);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    private PaymentSucceededEvent success() {
        return new PaymentSucceededEvent(metadata(EventType.PAYMENT_SUCCEEDED), UUID.randomUUID(), order.getId(), order.getCustomerId(), "SIMULATOR", "sim_payment", BigDecimal.TEN, "USD", now);
    }
    private InventoryCommittedEvent committed() {
        return new InventoryCommittedEvent(metadata(EventType.INVENTORY_COMMITTED), order.getId(), order.getInventoryReservationId(), now);
    }
    private EventMetadata metadata(EventType type) {
        return new EventMetadata(UUID.randomUUID(), type, 1, "ORDER", order.getId(), now, "commit-test", null, "test-service");
    }
}
