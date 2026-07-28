package com.orderflow.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory_reservation_item")
public class InventoryReservationItem {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false)
    private InventoryReservation reservation;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(nullable = false)
    private long quantity;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected InventoryReservationItem() {}

    InventoryReservationItem(
            InventoryReservation reservation, UUID productId, long quantity, Instant now) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        this.id = UUID.randomUUID();
        this.reservation = reservation;
        this.productId = productId;
        this.quantity = quantity;
        this.createdAt = now;
    }

    public UUID getProductId() {
        return productId;
    }

    public long getQuantity() {
        return quantity;
    }
}
