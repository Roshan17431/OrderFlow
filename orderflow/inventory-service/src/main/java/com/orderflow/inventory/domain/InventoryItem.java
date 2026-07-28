package com.orderflow.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "inventory_item")
public class InventoryItem {

    @Id
    private UUID id;

    @Column(name = "product_id", nullable = false, unique = true)
    private UUID productId;

    @Column(name = "on_hand_quantity", nullable = false)
    private long onHandQuantity;

    @Column(name = "reserved_quantity", nullable = false)
    private long reservedQuantity;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected InventoryItem() {}

    private InventoryItem(UUID productId, long onHandQuantity, Instant now) {
        if (onHandQuantity < 0) {
            throw new IllegalArgumentException("on-hand quantity cannot be negative");
        }
        this.id = UUID.randomUUID();
        this.productId = Objects.requireNonNull(productId);
        this.onHandQuantity = onHandQuantity;
        this.reservedQuantity = 0;
        this.createdAt = Objects.requireNonNull(now);
        this.updatedAt = now;
    }

    public static InventoryItem create(UUID productId, long onHandQuantity, Instant now) {
        return new InventoryItem(productId, onHandQuantity, now);
    }

    public long availableQuantity() {
        return onHandQuantity - reservedQuantity;
    }

    public void adjust(long delta, Instant now) {
        if (delta == 0) {
            throw new IllegalArgumentException("quantity delta cannot be zero");
        }
        long resulting;
        try {
            resulting = Math.addExact(onHandQuantity, delta);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("quantity adjustment is out of range", exception);
        }
        if (resulting < reservedQuantity) {
            throw new StockBelowReservedException();
        }
        onHandQuantity = resulting;
        updatedAt = Objects.requireNonNull(now);
    }

    public void reserve(long quantity, Instant now) {
        requirePositive(quantity);
        if (availableQuantity() < quantity) {
            throw new InsufficientStockException(productId, quantity, availableQuantity());
        }
        reservedQuantity = Math.addExact(reservedQuantity, quantity);
        updatedAt = Objects.requireNonNull(now);
    }

    public void release(long quantity, Instant now) {
        requirePositive(quantity);
        if (reservedQuantity < quantity) {
            throw new IllegalStateException("reserved quantity cannot become negative");
        }
        reservedQuantity -= quantity;
        updatedAt = Objects.requireNonNull(now);
    }

    private void requirePositive(long quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getProductId() {
        return productId;
    }

    public long getOnHandQuantity() {
        return onHandQuantity;
    }

    public long getReservedQuantity() {
        return reservedQuantity;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
