package com.orderflow.payment.repository;

import com.orderflow.payment.domain.Payment;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository
        extends JpaRepository<Payment, UUID>, JpaSpecificationExecutor<Payment> {
    Optional<Payment> findByOrderId(UUID orderId);
    Optional<Payment> findByProviderPaymentId(String providerPaymentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.orderId = :orderId")
    Optional<Payment> findByOrderIdForUpdate(@Param("orderId") UUID orderId);

    @Query(value = "SELECT id FROM payment WHERE payment_deadline <= :now AND created_at >= :oldest AND status IN ('PENDING','REQUIRES_ACTION','PROCESSING') ORDER BY payment_deadline,id LIMIT :limit", nativeQuery = true)
    List<UUID> findOverdueIds(@Param("now") Instant now, @Param("oldest") Instant oldest, @Param("limit") int limit);

    long countByPaymentDeadlineBeforeAndStatusIn(Instant now, java.util.Collection<com.orderflow.payment.domain.PaymentStatus> statuses);
}
