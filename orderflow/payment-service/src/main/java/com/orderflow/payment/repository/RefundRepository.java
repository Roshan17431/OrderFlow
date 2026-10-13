package com.orderflow.payment.repository;

import com.orderflow.payment.domain.Refund;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefundRepository extends JpaRepository<Refund, UUID> {
    @org.springframework.data.jpa.repository.Query("select r from Refund r where "
            + "r.status = com.orderflow.payment.domain.RefundStatus.PROCESSING "
            + "and r.providerRefundId is not null order by r.createdAt, r.id")
    java.util.List<Refund> findDemoProcessing(Pageable page);
    Page<Refund> findByPaymentId(UUID paymentId, Pageable pageable);
    Optional<Refund> findByProviderRefundId(String providerRefundId);
}
