package com.orderflow.order.service;

import com.orderflow.events.OrderCreatedItem;
import com.orderflow.order.domain.CustomerOrder;
import com.orderflow.order.dto.OrderHistoryResponse;
import com.orderflow.order.dto.OrderItemResponse;
import com.orderflow.order.dto.OrderResponse;
import com.orderflow.order.dto.ShippingAddressResponse;
import org.springframework.stereotype.Component;

@Component
public class OrderMapper {

    public OrderResponse response(CustomerOrder order) {
        return new OrderResponse(
                order.getId(),
                order.getCustomerId(),
                order.getStatus(),
                order.getFulfillmentStatus(),
                address(order),
                order.getCurrency(),
                order.getSubtotal(),
                order.getTotalAmount(),
                order.getCartVersion(),
                order.getCancellationReason(),
                order.getInventoryReservationId(),
                order.getInventoryExpiresAt(),
                order.getPaymentId(),
                order.getPaidAt(),
                order.getPaymentFailedAt(),
                order.getPaymentFailureCode(),
                order.getRefundedAmount(),
                order.getRefundedAt(),
                order.getInventoryReleasedAt(),
                order.getDeliveryId(),
                order.getDeliveryAgentId(),
                order.getDeliveryRequestedAt(),
                order.getDeliveryAssignedAt(),
                order.getPickedUpAt(),
                order.getOutForDeliveryAt(),
                order.getDeliveredAt(),
                order.getDeliveryFailedAt(),
                order.getDeliveryCancelledAt(),
                order.getItems().stream()
                        .map(item -> new OrderItemResponse(
                                item.getProductId(),
                                item.getProductName(),
                                item.getQuantity(),
                                item.getUnitPrice(),
                                item.getLineTotal(),
                                item.getCurrency(),
                                item.getProductVersion()))
                        .toList(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                order.getVersion());
    }

    private ShippingAddressResponse address(CustomerOrder order) {
        var value = order.getShippingAddress().snapshot();
        return new ShippingAddressResponse(value.recipientName(), value.addressLine1(),
                value.addressLine2(), value.city(), value.stateOrRegion(), value.postalCode(),
                value.countryCode(), value.deliveryInstructions());
    }

    public OrderCreatedItem eventItem(com.orderflow.order.domain.OrderItem item) {
        return new OrderCreatedItem(
                item.getProductId(),
                item.getProductName(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getLineTotal(),
                item.getCurrency(),
                item.getProductVersion());
    }

    public OrderHistoryResponse history(
            com.orderflow.order.domain.OrderStatusHistory history) {
        return new OrderHistoryResponse(
                history.getPreviousStatus(),
                history.getNewStatus(),
                history.getReasonCode(),
                history.getEventId(),
                history.getCorrelationId(),
                history.getChangedAt());
    }
}
