package com.orderflow.inventory.repository;

import com.orderflow.inventory.domain.InventoryItem;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, UUID> {

    Optional<InventoryItem> findByProductId(UUID productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select item from InventoryItem item where item.productId = :productId")
    Optional<InventoryItem> findByProductIdForUpdate(@Param("productId") UUID productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select item from InventoryItem item
            where item.productId in :productIds
            order by item.productId
            """)
    List<InventoryItem> findAllByProductIdForUpdate(
            @Param("productIds") Collection<UUID> productIds);
}
