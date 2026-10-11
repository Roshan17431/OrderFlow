package com.orderflow.payment.repository;

import com.orderflow.payment.domain.IdempotencyRecord;
import com.orderflow.payment.domain.IdempotencyScope;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID> {
    @Query("""
            select record from IdempotencyRecord record
            where record.scope = :scope
              and record.actorId = :actorId
              and record.key = :key
            """)
    Optional<IdempotencyRecord> findByScopeAndActorIdAndKey(
            @Param("scope") IdempotencyScope scope,
            @Param("actorId") UUID actorId,
            @Param("key") String key);
}
