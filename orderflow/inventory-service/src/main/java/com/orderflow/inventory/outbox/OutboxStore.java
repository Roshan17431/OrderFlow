package com.orderflow.inventory.outbox;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.orderflow.events.EventMetadata;
import com.orderflow.inventory.config.OutboxProperties;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class OutboxStore {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final OutboxProperties properties;
    private final Clock clock;

    public OutboxStore(
            JdbcTemplate jdbc,
            ObjectMapper objectMapper,
            OutboxProperties properties,
            Clock clock) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.clock = clock;
    }

    public void enqueue(String topic, UUID orderId, Object event, EventMetadata metadata) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Unable to serialize event", exception);
        }
        Instant now = Instant.now(clock);
        jdbc.update(
                """
                INSERT INTO outbox_event
                    (id, aggregate_type, aggregate_id, event_type, event_version,
                     topic, message_key, payload, correlation_id, causation_id,
                     status, attempts, available_at, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?, 'NEW', 0, ?, ?)
                """,
                metadata.eventId(),
                metadata.aggregateType(),
                metadata.aggregateId(),
                metadata.eventType().name(),
                metadata.eventVersion(),
                topic,
                orderId.toString(),
                payload,
                metadata.correlationId(),
                metadata.causationId(),
                Timestamp.from(now),
                Timestamp.from(now));
    }

    @Transactional
    public List<OutboxMessage> claim(String owner) {
        Instant now = Instant.now(clock);
        Instant staleBefore = now.minus(properties.lockTimeout());
        List<OutboxMessage> messages = jdbc.query(
                """
                SELECT id, topic, message_key, event_type, event_version,
                       payload::text, attempts
                FROM outbox_event current_event
                WHERE ((status = 'NEW' AND available_at <= ?)
                   OR (status = 'IN_PROGRESS' AND locked_at < ?))
                  AND NOT EXISTS (
                    SELECT 1 FROM outbox_event earlier
                    WHERE earlier.topic = current_event.topic
                      AND earlier.message_key = current_event.message_key
                      AND earlier.event_sequence < current_event.event_sequence
                      AND earlier.status <> 'PUBLISHED')
                ORDER BY event_sequence
                FOR UPDATE SKIP LOCKED
                LIMIT ?
                """,
                this::map,
                Timestamp.from(now),
                Timestamp.from(staleBefore),
                properties.batchSize());
        for (OutboxMessage message : messages) {
            jdbc.update(
                    """
                    UPDATE outbox_event
                    SET status = 'IN_PROGRESS', locked_at = ?, locked_by = ?
                    WHERE id = ?
                    """,
                    Timestamp.from(now),
                    owner,
                    message.id());
        }
        return messages;
    }

    @Transactional
    public void published(UUID id, String owner) {
        jdbc.update(
                """
                UPDATE outbox_event
                SET status = 'PUBLISHED', published_at = ?, locked_at = NULL,
                    locked_by = NULL, last_error_code = NULL
                WHERE id = ? AND status = 'IN_PROGRESS' AND locked_by = ?
                """,
                Timestamp.from(Instant.now(clock)),
                id,
                owner);
    }

    @Transactional
    public void failed(OutboxMessage message, String owner, String safeCode) {
        int attempts = message.attempts() + 1;
        boolean terminal = attempts >= properties.maxAttempts();
        Instant availableAt = Instant.now(clock).plus(backoff(
                properties.retryBaseDelay(), attempts));
        jdbc.update(
                """
                UPDATE outbox_event
                SET status = ?, attempts = ?, available_at = ?, last_error_code = ?,
                    locked_at = NULL, locked_by = NULL
                WHERE id = ? AND status = 'IN_PROGRESS' AND locked_by = ?
                """,
                terminal ? "FAILED" : "NEW",
                attempts,
                Timestamp.from(availableAt),
                bound(safeCode),
                message.id(),
                owner);
    }

    public long backlog() {
        Long count = jdbc.queryForObject(
                "SELECT count(*) FROM outbox_event WHERE status IN ('NEW', 'IN_PROGRESS')",
                Long.class);
        return count == null ? 0 : count;
    }

    public long failedCount() {
        Long count = jdbc.queryForObject(
                "SELECT count(*) FROM outbox_event WHERE status = 'FAILED'", Long.class);
        return count == null ? 0 : count;
    }

    public static Duration backoff(Duration base, int attempts) {
        int exponent = Math.min(Math.max(attempts - 1, 0), 10);
        try {
            return base.multipliedBy(1L << exponent);
        } catch (ArithmeticException exception) {
            return Duration.ofHours(1);
        }
    }

    private OutboxMessage map(ResultSet result, int row) throws SQLException {
        return new OutboxMessage(
                result.getObject("id", UUID.class),
                result.getString("topic"),
                result.getString("message_key"),
                result.getString("event_type"),
                result.getInt("event_version"),
                result.getString("payload"),
                result.getInt("attempts"));
    }

    private String bound(String value) {
        String safe = value == null || value.isBlank() ? "KAFKA_PUBLICATION_FAILED" : value;
        return safe.substring(0, Math.min(200, safe.length()));
    }
}
