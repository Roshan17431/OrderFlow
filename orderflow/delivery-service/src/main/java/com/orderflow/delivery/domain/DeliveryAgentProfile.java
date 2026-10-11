package com.orderflow.delivery.domain;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="delivery_agent_profile")
public class DeliveryAgentProfile {
 @Id private UUID id; @Column(name="user_id",nullable=false,unique=true) private UUID userId;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) private DeliveryAgentAvailability availability;
 @Column(name="last_assigned_at") private Instant lastAssignedAt; @Column(name="created_at",nullable=false) private Instant createdAt; @Column(name="updated_at",nullable=false) private Instant updatedAt; @Version private long version;
 protected DeliveryAgentProfile(){}
 public static DeliveryAgentProfile register(UUID userId,Instant now){DeliveryAgentProfile p=new DeliveryAgentProfile();p.id=UUID.randomUUID();p.userId=userId;p.availability=DeliveryAgentAvailability.OFFLINE;p.createdAt=now;p.updatedAt=now;return p;}
 public void setUserAvailability(DeliveryAgentAvailability next,Instant now){if(next==DeliveryAgentAvailability.BUSY)throw new IllegalArgumentException("BUSY is service-controlled");if(availability==DeliveryAgentAvailability.BUSY)throw new IllegalStateException("Busy agent cannot change availability");availability=next;updatedAt=now;}
 public void assigned(Instant now){if(availability!=DeliveryAgentAvailability.AVAILABLE)throw new IllegalStateException("Agent is not available");availability=DeliveryAgentAvailability.BUSY;lastAssignedAt=now;updatedAt=now;}
 public void released(Instant now){if(availability==DeliveryAgentAvailability.BUSY){availability=DeliveryAgentAvailability.AVAILABLE;updatedAt=now;}}
 public UUID getId(){return id;} public UUID getUserId(){return userId;} public DeliveryAgentAvailability getAvailability(){return availability;} public Instant getLastAssignedAt(){return lastAssignedAt;} public Instant getUpdatedAt(){return updatedAt;}
}
