package com.orderflow.payment.repository;

import com.orderflow.payment.domain.Refund;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefundRepository extends JpaRepository<Refund, UUID> {
    Page<Refund> findByPaymentId(UUID paymentId, Pageable pageable);
    Optional<Refund> findByProviderRefundId(String providerRefundId);
}
