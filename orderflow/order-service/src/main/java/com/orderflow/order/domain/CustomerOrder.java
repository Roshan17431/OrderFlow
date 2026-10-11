package com.orderflow.order.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Embedded;
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
import java.util.Set;
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

    @Enumerated(EnumType.STRING)
    @Column(name = "fulfillment_status", nullable = false, length = 40)
    private FulfillmentStatus fulfillmentStatus;

    @Embedded
    private ShippingAddress shippingAddress;

    @Column(name = "delivery_id") private UUID deliveryId;
    @Column(name = "delivery_agent_id") private UUID deliveryAgentId;
    @Column(name = "delivery_requested_at") private Instant deliveryRequestedAt;
    @Column(name = "delivery_assigned_at") private Instant deliveryAssignedAt;
    @Column(name = "picked_up_at") private Instant pickedUpAt;
    @Column(name = "out_for_delivery_at") private Instant outForDeliveryAt;
    @Column(name = "delivered_at") private Instant deliveredAt;
    @Column(name = "delivery_failed_at") private Instant deliveryFailedAt;
    @Column(name = "delivery_cancelled_at") private Instant deliveryCancelledAt;

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

    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "payment_failed_at")
    private Instant paymentFailedAt;

    @Column(name = "payment_failure_code", length = 80)
    private String paymentFailureCode;

    @Column(name = "refunded_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal refundedAmount;

    @Column(name = "refunded_at")
    private Instant refundedAt;

    @Column(name = "inventory_released_at")
    private Instant inventoryReleasedAt;

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
            ShippingAddress shippingAddress,
            Instant now,
            String correlationId) {
        if (subtotal.signum() < 0) {
            throw new IllegalArgumentException("subtotal cannot be negative");
        }
        CustomerOrder order = new CustomerOrder();
        order.id = id;
        order.customerId = customerId;
        order.status = OrderStatus.INVENTORY_PENDING;
        order.fulfillmentStatus = FulfillmentStatus.NOT_REQUESTED;
        order.shippingAddress = shippingAddress;
        order.currency = currency;
        order.subtotal = subtotal;
        order.totalAmount = subtotal;
        order.refundedAmount = BigDecimal.ZERO.setScale(2);
        order.cartVersion = cartVersion;
        order.createdAt = now;
        order.updatedAt = now;
        order.history.add(new OrderStatusHistory(
                order, null, OrderStatus.INVENTORY_PENDING, "ORDER_CREATED",
                null, correlationId, now));
        return order;
    }

    public static CustomerOrder create(
            UUID id, UUID customerId, String currency, BigDecimal subtotal,
            long cartVersion, Instant now, String correlationId) {
        return create(id, customerId, currency, subtotal, cartVersion,
                new ShippingAddress(new com.orderflow.events.ShippingAddressSnapshot(
                        "Test recipient", "Test address", null, "Test city",
                        "Test region", "00000", "US", null)), now, correlationId);
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

    public void paymentSucceeded(UUID paymentId, Instant paidAt) {
        this.paymentId = paymentId;
        this.paidAt = paidAt;
        this.updatedAt = paidAt;
    }

    public void paymentFailed(UUID paymentId, String failureCode, Instant failedAt) {
        this.paymentId = paymentId;
        this.paymentFailureCode = failureCode;
        this.paymentFailedAt = failedAt;
        this.updatedAt = failedAt;
    }

    public void paymentRefunded(BigDecimal cumulativeAmount, Instant refundedAt) {
        if (cumulativeAmount == null
                || cumulativeAmount.signum() < 0
                || cumulativeAmount.compareTo(totalAmount) > 0
                || cumulativeAmount.compareTo(refundedAmount) < 0) {
            throw new IllegalArgumentException("Invalid cumulative refund amount");
        }
        this.refundedAmount = cumulativeAmount;
        this.refundedAt = refundedAt;
        this.updatedAt = refundedAt;
    }

    public void inventoryReleased(Instant releasedAt) {
        if (inventoryReleasedAt == null || releasedAt.isAfter(inventoryReleasedAt)) {
            inventoryReleasedAt = releasedAt;
            updatedAt = releasedAt;
        }
    }

    public void recordWorkflowEvent(
            String reason, UUID eventId, String correlationId, Instant now) {
        history.add(new OrderStatusHistory(
                this, status, status, reason, eventId, correlationId, now));
        updatedAt = now;
    }

    public void requestDelivery(Instant now) {
        if (fulfillmentStatus != FulfillmentStatus.NOT_REQUESTED) return;
        fulfillmentStatus = FulfillmentStatus.DELIVERY_PENDING;
        deliveryRequestedAt = now;
        updatedAt = now;
    }

    public void deliveryAssigned(UUID deliveryId, UUID agentId, Instant at) {
        requireFulfillment(FulfillmentStatus.DELIVERY_PENDING);
        this.deliveryId = deliveryId; this.deliveryAgentId = agentId;
        fulfillmentStatus = FulfillmentStatus.DELIVERY_ASSIGNED; deliveryAssignedAt = at; updatedAt = at;
    }

    public void deliveryPickedUp(Instant at) { requireFulfillment(FulfillmentStatus.DELIVERY_ASSIGNED); fulfillmentStatus = FulfillmentStatus.PICKED_UP; pickedUpAt = at; updatedAt = at; }
    public void deliveryOutForDelivery(Instant at) { requireFulfillment(FulfillmentStatus.PICKED_UP); fulfillmentStatus = FulfillmentStatus.OUT_FOR_DELIVERY; outForDeliveryAt = at; updatedAt = at; }
    public void deliveryDelivered(Instant at) { requireFulfillment(FulfillmentStatus.OUT_FOR_DELIVERY); fulfillmentStatus = FulfillmentStatus.DELIVERED; deliveredAt = at; updatedAt = at; }
    public void deliveryFailed(Instant at) { if (!Set.of(FulfillmentStatus.DELIVERY_ASSIGNED, FulfillmentStatus.PICKED_UP, FulfillmentStatus.OUT_FOR_DELIVERY).contains(fulfillmentStatus)) throw new IllegalStateException("Invalid fulfillment transition"); fulfillmentStatus = FulfillmentStatus.DELIVERY_FAILED; deliveryFailedAt = at; updatedAt = at; }
    public void requestDeliveryCancellation(Instant at) { if (!Set.of(FulfillmentStatus.DELIVERY_PENDING, FulfillmentStatus.DELIVERY_ASSIGNED, FulfillmentStatus.PICKED_UP).contains(fulfillmentStatus)) throw new IllegalStateException("Delivery cannot be cancelled"); fulfillmentStatus = FulfillmentStatus.DELIVERY_CANCEL_REQUESTED; updatedAt = at; }
    public void deliveryCancelled(Instant at) { if (!Set.of(FulfillmentStatus.DELIVERY_CANCEL_REQUESTED, FulfillmentStatus.DELIVERY_PENDING).contains(fulfillmentStatus)) throw new IllegalStateException("Invalid fulfillment transition"); fulfillmentStatus = FulfillmentStatus.DELIVERY_CANCELLED; deliveryCancelledAt = at; updatedAt = at; }
    private void requireFulfillment(FulfillmentStatus required) { if (fulfillmentStatus != required) throw new IllegalStateException("Invalid fulfillment transition"); }

    public UUID getId() {
        return id;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public OrderStatus getStatus() {
        return status;
    }
    public FulfillmentStatus getFulfillmentStatus() { return fulfillmentStatus; }
    public ShippingAddress getShippingAddress() { return shippingAddress; }
    public UUID getDeliveryId() { return deliveryId; }
    public UUID getDeliveryAgentId() { return deliveryAgentId; }
    public Instant getDeliveryRequestedAt() { return deliveryRequestedAt; }
    public Instant getDeliveryAssignedAt() { return deliveryAssignedAt; }
    public Instant getPickedUpAt() { return pickedUpAt; }
    public Instant getOutForDeliveryAt() { return outForDeliveryAt; }
    public Instant getDeliveredAt() { return deliveredAt; }
    public Instant getDeliveryFailedAt() { return deliveryFailedAt; }
    public Instant getDeliveryCancelledAt() { return deliveryCancelledAt; }

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

    public UUID getPaymentId() { return paymentId; }
    public Instant getPaidAt() { return paidAt; }
    public Instant getPaymentFailedAt() { return paymentFailedAt; }
    public String getPaymentFailureCode() { return paymentFailureCode; }
    public BigDecimal getRefundedAmount() { return refundedAmount; }
    public Instant getRefundedAt() { return refundedAt; }
    public Instant getInventoryReleasedAt() { return inventoryReleasedAt; }

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
