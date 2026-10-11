package com.orderflow.delivery.domain;
import jakarta.persistence.*;import java.time.Instant;import java.util.UUID;
@Entity @Table(name="delivery_status_history") public class DeliveryStatusHistory {
 @Id private UUID id; @Column(name="delivery_id",nullable=false) private UUID deliveryId;
 @Enumerated(EnumType.STRING) @Column(name="previous_status",length=32) private DeliveryStatus previousStatus;
 @Enumerated(EnumType.STRING) @Column(name="new_status",nullable=false,length=32) private DeliveryStatus newStatus;
 @Column(name="actor_user_id") private UUID actorUserId; @Column(name="source_event_id") private UUID sourceEventId; @Column(name="correlation_id",length=128) private String correlationId; @Column(name="changed_at",nullable=false) private Instant changedAt;
 protected DeliveryStatusHistory(){} public DeliveryStatusHistory(UUID deliveryId,DeliveryStatus previous,DeliveryStatus next,UUID actor,UUID event,String correlation,Instant at){id=UUID.randomUUID();this.deliveryId=deliveryId;previousStatus=previous;newStatus=next;actorUserId=actor;sourceEventId=event;correlationId=correlation;changedAt=at;}
 public DeliveryStatus getPreviousStatus(){return previousStatus;} public DeliveryStatus getNewStatus(){return newStatus;} public UUID getActorUserId(){return actorUserId;} public UUID getSourceEventId(){return sourceEventId;} public String getCorrelationId(){return correlationId;} public Instant getChangedAt(){return changedAt;}
}
