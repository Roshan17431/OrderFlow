package com.orderflow.payment.outbox;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.orderflow.events.EventMetadata;
import com.orderflow.payment.config.OutboxProperties;
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
            JdbcTemplate jdbc, ObjectMapper objectMapper,
            OutboxProperties properties, Clock clock) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.clock = clock;
    }

    public void enqueue(String topic, UUID orderId, Object event, EventMetadata metadata) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            Instant now = Instant.now(clock);
            jdbc.update(
                    """
                    INSERT INTO outbox_event
                        (id, aggregate_type, aggregate_id, event_type, event_version,
                         topic, message_key, payload, correlation_id, causation_id,
                         status, attempts, available_at, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?,
                            'NEW', 0, ?, ?)
                    """,
                    metadata.eventId(), metadata.aggregateType(), metadata.aggregateId(),
                    metadata.eventType().name(), metadata.eventVersion(), topic,
                    orderId.toString(), payload, metadata.correlationId(),
                    metadata.causationId(), Timestamp.from(now), Timestamp.from(now));
        } catch (JacksonException exception) {
            throw new IllegalStateException("Unable to serialize payment event", exception);
        }
    }

    @Transactional
    public List<OutboxMessage> claim(String owner) {
        Instant now = Instant.now(clock);
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
                this::map, Timestamp.from(now),
                Timestamp.from(now.minus(properties.lockTimeout())),
                properties.batchSize());
        messages.forEach(message -> jdbc.update(
                """
                UPDATE outbox_event SET status = 'IN_PROGRESS', locked_at = ?,
                    locked_by = ? WHERE id = ?
                """,
                Timestamp.from(now), owner, message.id()));
        return messages;
    }

    @Transactional
    public void published(UUID id, String owner) {
        jdbc.update(
                """
                UPDATE outbox_event SET status = 'PUBLISHED', published_at = ?,
                    locked_at = NULL, locked_by = NULL, last_error_code = NULL
                WHERE id = ? AND status = 'IN_PROGRESS' AND locked_by = ?
                """,
                Timestamp.from(Instant.now(clock)), id, owner);
    }

    @Transactional
    public void failed(OutboxMessage message, String owner, String code) {
        int attempts = message.attempts() + 1;
        jdbc.update(
                """
                UPDATE outbox_event SET status = ?, attempts = ?, available_at = ?,
                    last_error_code = ?, locked_at = NULL, locked_by = NULL
                WHERE id = ? AND status = 'IN_PROGRESS' AND locked_by = ?
                """,
                attempts >= properties.maxAttempts() ? "FAILED" : "NEW",
                attempts,
                Timestamp.from(Instant.now(clock).plus(backoff(
                        properties.retryBaseDelay(), attempts))),
                safeCode(code), message.id(), owner);
    }

    public long backlog() {
        Long count = jdbc.queryForObject(
                "SELECT count(*) FROM outbox_event WHERE status IN ('NEW','IN_PROGRESS')",
                Long.class);
        return count == null ? 0 : count;
    }

    public long failedCount() {
        Long count = jdbc.queryForObject(
                "SELECT count(*) FROM outbox_event WHERE status = 'FAILED'", Long.class);
        return count == null ? 0 : count;
    }

    public static Duration backoff(Duration base, int attempts) {
        try {
            return base.multipliedBy(1L << Math.min(Math.max(attempts - 1, 0), 10));
        } catch (ArithmeticException exception) {
            return Duration.ofHours(1);
        }
    }

    private OutboxMessage map(ResultSet row, int index) throws SQLException {
        return new OutboxMessage(
                row.getObject("id", UUID.class), row.getString("topic"),
                row.getString("message_key"), row.getString("event_type"),
                row.getInt("event_version"), row.getString("payload"),
                row.getInt("attempts"));
    }

    private String safeCode(String code) {
        String value = code == null || code.isBlank() ? "KAFKA_PUBLICATION_FAILED" : code;
        return value.substring(0, Math.min(200, value.length()));
    }
}
