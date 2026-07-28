package com.orderflow.inventory.service;

import com.orderflow.inventory.domain.InventoryAdjustment;
import com.orderflow.inventory.domain.InventoryItem;
import com.orderflow.inventory.domain.InventoryReservation;
import com.orderflow.inventory.dto.InventoryAdjustmentResponse;
import com.orderflow.inventory.dto.InventoryStockResponse;
import com.orderflow.inventory.dto.ReservationItemResponse;
import com.orderflow.inventory.dto.ReservationResponse;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class InventoryMapper {

    public InventoryStockResponse stock(UUID productId, InventoryItem item) {
        if (item == null) {
            return new InventoryStockResponse(productId, 0, 0, 0, 0, null);
        }
        return new InventoryStockResponse(
                item.getProductId(),
                item.getOnHandQuantity(),
                item.getReservedQuantity(),
                item.availableQuantity(),
                item.getVersion(),
                item.getUpdatedAt());
    }

    public InventoryAdjustmentResponse adjustment(InventoryAdjustment adjustment) {
        return new InventoryAdjustmentResponse(
                adjustment.getId(),
                adjustment.getProductId(),
                adjustment.getQuantityDelta(),
                adjustment.getResultingOnHand(),
                adjustment.getResultingReserved(),
                adjustment.getReason(),
                adjustment.getReference(),
                adjustment.getActorUserId(),
                adjustment.getCreatedAt(),
                adjustment.getCorrelationId());
    }

    public ReservationResponse reservation(InventoryReservation reservation) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getOrderId(),
                reservation.getStatus(),
                reservation.getExpiresAt(),
                reservation.getReleasedAt(),
                reservation.getItems().stream()
                        .map(item -> new ReservationItemResponse(
                                item.getProductId(), item.getQuantity()))
                        .toList(),
                reservation.getVersion());
    }
}
