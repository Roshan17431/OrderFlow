package com.orderflow.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory_adjustment")
public class InventoryAdjustment {

    @Id
    private UUID id;

    @Column(name = "inventory_item_id", nullable = false)
    private UUID inventoryItemId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "quantity_delta", nullable = false)
    private long quantityDelta;

    @Column(name = "resulting_on_hand", nullable = false)
    private long resultingOnHand;

    @Column(name = "resulting_reserved", nullable = false)
    private long resultingReserved;

    @Column(nullable = false, length = 120)
    private String reason;

    @Column(length = 200)
    private String reference;

    @Column(name = "actor_user_id", nullable = false)
    private UUID actorUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "correlation_id", length = 128)
    private String correlationId;

    protected InventoryAdjustment() {}

    public InventoryAdjustment(
            InventoryItem item,
            long quantityDelta,
            String reason,
            String reference,
            UUID actorUserId,
            Instant createdAt,
            String correlationId) {
        this.id = UUID.randomUUID();
        this.inventoryItemId = item.getId();
        this.productId = item.getProductId();
        this.quantityDelta = quantityDelta;
        this.resultingOnHand = item.getOnHandQuantity();
        this.resultingReserved = item.getReservedQuantity();
        this.reason = reason;
        this.reference = reference;
        this.actorUserId = actorUserId;
        this.createdAt = createdAt;
        this.correlationId = correlationId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProductId() {
        return productId;
    }

    public long getQuantityDelta() {
        return quantityDelta;
    }

    public long getResultingOnHand() {
        return resultingOnHand;
    }

    public long getResultingReserved() {
        return resultingReserved;
    }

    public String getReason() {
        return reason;
    }

    public String getReference() {
        return reference;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getCorrelationId() {
        return correlationId;
    }
}
