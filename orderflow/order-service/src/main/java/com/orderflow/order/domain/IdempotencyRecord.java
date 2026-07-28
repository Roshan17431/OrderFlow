package com.orderflow.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "idempotency_record")
public class IdempotencyRecord {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private IdempotencyScope scope;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "resource_id")
    private UUID resourceId;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected IdempotencyRecord() {}

    public IdempotencyRecord(
            IdempotencyScope scope,
            UUID actorId,
            String idempotencyKey,
            String requestHash,
            Instant now) {
        this.id = UUID.randomUUID();
        this.scope = scope;
        this.actorId = actorId;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.createdAt = now;
    }

    public void complete(UUID resourceId, int responseStatus, Instant now) {
        this.resourceId = resourceId;
        this.responseStatus = responseStatus;
        this.completedAt = now;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public UUID getResourceId() {
        return resourceId;
    }

    public Integer getResponseStatus() {
        return responseStatus;
    }

    public boolean completed() {
        return completedAt != null && resourceId != null;
    }
}
