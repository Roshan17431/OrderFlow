package com.orderflow.delivery.repository;
import com.orderflow.delivery.domain.DeliveryAgentProfile;import java.util.Optional;import java.util.UUID;import org.springframework.data.jpa.repository.*;import org.springframework.data.repository.query.Param;
public interface DeliveryAgentRepository extends JpaRepository<DeliveryAgentProfile,UUID>{
 Optional<DeliveryAgentProfile> findByUserId(UUID userId);
 @Query(value="SELECT * FROM delivery_agent_profile WHERE user_id=:userId FOR UPDATE",nativeQuery=true) Optional<DeliveryAgentProfile> lockByUserId(@Param("userId") UUID userId);
 @Query(value="SELECT * FROM delivery_agent_profile WHERE id=:id FOR UPDATE",nativeQuery=true) Optional<DeliveryAgentProfile> lockById(@Param("id") UUID id);
 @Query(value="SELECT * FROM delivery_agent_profile WHERE availability='AVAILABLE' ORDER BY last_assigned_at NULLS FIRST,created_at,id FOR UPDATE SKIP LOCKED LIMIT 1",nativeQuery=true) Optional<DeliveryAgentProfile> lockNextAvailable();
}
