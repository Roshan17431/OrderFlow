package com.orderflow.inventory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InventoryDomainTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void adjustmentsAndAvailabilityPreserveQuantityInvariants() {
        InventoryItem item = InventoryItem.create(UUID.randomUUID(), 10, NOW);
        assertThat(item.availableQuantity()).isEqualTo(10);

        item.reserve(4, NOW.plusSeconds(1));
        assertThat(item.getReservedQuantity()).isEqualTo(4);
        assertThat(item.availableQuantity()).isEqualTo(6);

        item.adjust(5, NOW.plusSeconds(2));
        assertThat(item.getOnHandQuantity()).isEqualTo(15);
        item.adjust(-7, NOW.plusSeconds(3));
        assertThat(item.getOnHandQuantity()).isEqualTo(8);
        assertThat(item.availableQuantity()).isEqualTo(4);

        assertThatThrownBy(() -> item.adjust(-5, NOW))
                .isInstanceOf(StockBelowReservedException.class);
        assertThatThrownBy(() -> item.adjust(0, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> item.reserve(5, NOW))
                .isInstanceOf(InsufficientStockException.class);
    }

    @Test
    void releaseAndReservationStateTransitionsAreIdempotent() {
        UUID productId = UUID.randomUUID();
        InventoryItem item = InventoryItem.create(productId, 10, NOW);
        item.reserve(3, NOW);
        item.release(3, NOW);
        assertThat(item.getReservedQuantity()).isZero();
        assertThatThrownBy(() -> item.release(1, NOW))
                .isInstanceOf(IllegalStateException.class);

        InventoryReservation reservation = InventoryReservation.reserved(
                UUID.randomUUID(), NOW.plusSeconds(60), NOW);
        reservation.addItem(productId, 3, NOW);
        assertThat(reservation.release(NOW.plusSeconds(2), false)).isTrue();
        assertThat(reservation.release(NOW.plusSeconds(3), false)).isFalse();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.RELEASED);
    }

    @Test
    void rejectedReservationNeverContainsStockItems() {
        InventoryReservation reservation =
                InventoryReservation.rejected(UUID.randomUUID(), "INSUFFICIENT_STOCK", NOW);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.REJECTED);
        assertThat(reservation.getItems()).isEmpty();
        assertThatThrownBy(() -> reservation.addItem(UUID.randomUUID(), 1, NOW))
                .isInstanceOf(IllegalStateException.class);
    }
}
