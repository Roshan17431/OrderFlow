package com.orderflow.order.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "customer_order")
public class CustomerOrder {

    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OrderStatus status;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "cart_version", nullable = false)
    private long cartVersion;

    @Column(name = "cancellation_reason", length = 200)
    private String cancellationReason;

    @Column(name = "inventory_reservation_id")
    private UUID inventoryReservationId;

    @Column(name = "inventory_expires_at")
    private Instant inventoryExpiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = false)
    @OrderBy("createdAt ASC, id ASC")
    private List<OrderItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = false)
    @OrderBy("changedAt ASC, id ASC")
    private List<OrderStatusHistory> history = new ArrayList<>();

    protected CustomerOrder() {}

    public static CustomerOrder create(
            UUID id,
            UUID customerId,
            String currency,
            BigDecimal subtotal,
            long cartVersion,
            Instant now,
            String correlationId) {
        if (subtotal.signum() < 0) {
            throw new IllegalArgumentException("subtotal cannot be negative");
        }
        CustomerOrder order = new CustomerOrder();
        order.id = id;
        order.customerId = customerId;
        order.status = OrderStatus.INVENTORY_PENDING;
        order.currency = currency;
        order.subtotal = subtotal;
        order.totalAmount = subtotal;
        order.cartVersion = cartVersion;
        order.createdAt = now;
        order.updatedAt = now;
        order.history.add(new OrderStatusHistory(
                order, null, OrderStatus.INVENTORY_PENDING, "ORDER_CREATED",
                null, correlationId, now));
        return order;
    }

    public void addItem(
            UUID productId,
            String productName,
            long quantity,
            BigDecimal unitPrice,
            Long productVersion,
            Instant now) {
        items.add(new OrderItem(
                this, productId, productName, quantity, unitPrice, currency,
                productVersion, now));
    }

    public void transition(
            OrderStatus next,
            String reason,
            UUID eventId,
            String correlationId,
            Instant now) {
        OrderStatus previous = status;
        status = next;
        updatedAt = now;
        history.add(new OrderStatusHistory(
                this, previous, next, reason, eventId, correlationId, now));
    }

    public void requestCancellation(String reason) {
        cancellationReason = reason;
    }

    public void attachReservation(UUID reservationId, Instant expiresAt, Instant now) {
        inventoryReservationId = reservationId;
        inventoryExpiresAt = expiresAt;
        updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public long getCartVersion() {
        return cartVersion;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public UUID getInventoryReservationId() {
        return inventoryReservationId;
    }

    public Instant getInventoryExpiresAt() {
        return inventoryExpiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public List<OrderStatusHistory> getHistory() {
        return Collections.unmodifiableList(history);
    }
}
