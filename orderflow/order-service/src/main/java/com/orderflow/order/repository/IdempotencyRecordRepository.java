package com.orderflow.order.repository;

import com.orderflow.order.domain.IdempotencyRecord;
import com.orderflow.order.domain.IdempotencyScope;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyRecordRepository
        extends JpaRepository<IdempotencyRecord, UUID> {
    Optional<IdempotencyRecord> findByScopeAndActorIdAndIdempotencyKey(
            IdempotencyScope scope, UUID actorId, String idempotencyKey);
}
