package com.orderflow.payment.repository;

import com.orderflow.payment.domain.PaymentCancellationTombstone;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentCancellationTombstoneRepository
        extends JpaRepository<PaymentCancellationTombstone, UUID> {}
