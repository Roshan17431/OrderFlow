package com.orderflow.inventory.repository;

import com.orderflow.inventory.domain.InventoryAdjustment;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryAdjustmentRepository
        extends JpaRepository<InventoryAdjustment, UUID> {
    Page<InventoryAdjustment> findByProductId(UUID productId, Pageable pageable);
}
