package com.orderflow.inventory.domain;

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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "inventory_reservation")
public class InventoryReservation {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReservationStatus status;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    @Version
    @Column(nullable = false)
    private long version;

    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = false)
    @OrderBy("productId ASC")
    private List<InventoryReservationItem> items = new ArrayList<>();

    protected InventoryReservation() {}

    public static InventoryReservation reserved(UUID orderId, Instant expiresAt, Instant now) {
        InventoryReservation reservation = new InventoryReservation();
        reservation.id = UUID.randomUUID();
        reservation.orderId = orderId;
        reservation.status = ReservationStatus.RESERVED;
        reservation.expiresAt = expiresAt;
        reservation.createdAt = now;
        reservation.updatedAt = now;
        return reservation;
    }

    public static InventoryReservation rejected(
            UUID orderId, String failureReason, Instant now) {
        InventoryReservation reservation = new InventoryReservation();
        reservation.id = UUID.randomUUID();
        reservation.orderId = orderId;
        reservation.status = ReservationStatus.REJECTED;
        reservation.failureReason = failureReason;
        reservation.createdAt = now;
        reservation.updatedAt = now;
        return reservation;
    }

    public void addItem(UUID productId, long quantity, Instant now) {
        if (status != ReservationStatus.RESERVED) {
            throw new IllegalStateException("Only reserved reservations can contain items");
        }
        items.add(new InventoryReservationItem(this, productId, quantity, now));
    }

    public boolean release(Instant now, boolean expired) {
        if (status == ReservationStatus.RELEASED || status == ReservationStatus.EXPIRED
                || status == ReservationStatus.FULFILLED) {
            return false;
        }
        if (expired && status == ReservationStatus.COMMITTED) {
            return false;
        }
        if (status == ReservationStatus.REJECTED) {
            status = ReservationStatus.RELEASED;
            releasedAt = now;
            updatedAt = now;
            return false;
        }
        status = expired ? ReservationStatus.EXPIRED : ReservationStatus.RELEASED;
        releasedAt = now;
        updatedAt = now;
        return true;
    }

    public void commit(Instant now) {
        if (status != ReservationStatus.RESERVED) {
            throw new IllegalStateException("Only reserved inventory can be committed");
        }
        status = ReservationStatus.COMMITTED;
        updatedAt = now;
    }

    public void fulfill(Instant now) {
        if (status != ReservationStatus.COMMITTED) {
            throw new IllegalStateException("Only committed inventory can be fulfilled");
        }
        status = ReservationStatus.FULFILLED;
        updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getReleasedAt() {
        return releasedAt;
    }

    public long getVersion() {
        return version;
    }

    public List<InventoryReservationItem> getItems() {
        return Collections.unmodifiableList(items);
    }
}
