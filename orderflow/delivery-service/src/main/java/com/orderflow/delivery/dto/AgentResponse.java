package com.orderflow.delivery.dto;
import com.orderflow.delivery.domain.DeliveryAgentAvailability;
import java.time.Instant;
import java.util.UUID;
public record AgentResponse(UUID userId,DeliveryAgentAvailability availability,
        UUID activeDeliveryId,Instant updatedAt,UUID profileId) {
    public AgentResponse(UUID userId,DeliveryAgentAvailability availability,
            UUID activeDeliveryId,Instant updatedAt) {
        this(userId,availability,activeDeliveryId,updatedAt,null);
    }
}
