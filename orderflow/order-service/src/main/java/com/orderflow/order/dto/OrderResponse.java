package com.orderflow.order.dto;

import com.orderflow.order.domain.OrderStatus;
import com.orderflow.order.domain.FulfillmentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        UUID customerId,
        OrderStatus status,
        FulfillmentStatus fulfillmentStatus,
        ShippingAddressResponse shippingAddress,
        String currency,
        BigDecimal subtotal,
        BigDecimal totalAmount,
        long cartVersion,
        String cancellationReason,
        UUID inventoryReservationId,
        Instant inventoryReservationExpiresAt,
        UUID paymentId,
        Instant paidAt,
        Instant paymentFailedAt,
        String paymentFailureCode,
        BigDecimal refundedAmount,
        Instant refundedAt,
        Instant inventoryReleasedAt,
        UUID deliveryId,
        UUID deliveryAgentId,
        Instant deliveryRequestedAt,
        Instant deliveryAssignedAt,
        Instant pickedUpAt,
        Instant outForDeliveryAt,
        Instant deliveredAt,
        Instant deliveryFailedAt,
        Instant deliveryCancelledAt,
        List<OrderItemResponse> items,
        Instant createdAt,
        Instant updatedAt,
        long version) {
    public OrderResponse {
        items = List.copyOf(items);
    }
}
