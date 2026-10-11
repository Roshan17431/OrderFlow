package com.orderflow.payment.repository;

import com.orderflow.payment.domain.PaymentAttempt;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, UUID> {
    Page<PaymentAttempt> findByPaymentId(UUID paymentId, Pageable pageable);
    List<PaymentAttempt> findByPaymentIdOrderByAttemptNumberDesc(UUID paymentId);
    Optional<PaymentAttempt> findFirstByPaymentIdOrderByAttemptNumberDesc(UUID paymentId);
    long countByPaymentId(UUID paymentId);
}
