package com.orderflow.delivery.repository;
import com.orderflow.delivery.domain.Delivery;import com.orderflow.delivery.domain.DeliveryStatus;import jakarta.persistence.LockModeType;import java.util.Optional;import java.util.UUID;import org.springframework.data.domain.Page;import org.springframework.data.domain.Pageable;import org.springframework.data.jpa.repository.*;import org.springframework.data.repository.query.Param;
public interface DeliveryRepository extends JpaRepository<Delivery,UUID>{
 Optional<Delivery> findByOrderId(UUID orderId);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select d from Delivery d where d.orderId=:orderId") Optional<Delivery> lockByOrderId(@Param("orderId") UUID orderId); Page<Delivery> findByCustomerId(UUID customerId,Pageable p); Page<Delivery> findByDeliveryAgentId(UUID id,Pageable p);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select d from Delivery d where d.id=:id") Optional<Delivery> lockById(@Param("id") UUID id);
 @Query(value="SELECT * FROM delivery WHERE status='PENDING_ASSIGNMENT' ORDER BY created_at,id FOR UPDATE SKIP LOCKED LIMIT 1",nativeQuery=true) Optional<Delivery> lockOldestPending();
 long countByStatus(DeliveryStatus status);
 @Query(value="SELECT id FROM delivery WHERE status='PENDING_ASSIGNMENT' ORDER BY created_at,id LIMIT :limit",nativeQuery=true) java.util.List<UUID> findPendingAssignmentIds(@Param("limit") int limit);
}
