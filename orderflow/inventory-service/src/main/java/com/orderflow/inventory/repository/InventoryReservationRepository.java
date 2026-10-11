package com.orderflow.inventory.repository;

import com.orderflow.inventory.domain.InventoryReservation;
import com.orderflow.inventory.domain.ReservationStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryReservationRepository
        extends JpaRepository<InventoryReservation, UUID> {

    Optional<InventoryReservation> findByOrderId(UUID orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select distinct reservation from InventoryReservation reservation
            left join fetch reservation.items
            where reservation.orderId = :orderId
            """)
    Optional<InventoryReservation> findByOrderIdForUpdate(@Param("orderId") UUID orderId);

    @Query(
            value = """
                    SELECT * FROM inventory_reservation
                    WHERE status = 'RESERVED' AND expires_at <= :now
                    ORDER BY expires_at, id
                    FOR UPDATE SKIP LOCKED
                    LIMIT :limit
                    """,
            nativeQuery = true)
    List<InventoryReservation> findExpiredForUpdate(
            @Param("now") Instant now, @Param("limit") int limit);
    long countByStatusAndExpiresAtLessThanEqual(ReservationStatus status, Instant now);
}
