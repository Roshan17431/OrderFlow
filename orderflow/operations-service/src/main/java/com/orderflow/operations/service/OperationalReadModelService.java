package com.orderflow.operations.service;

import com.orderflow.events.EventMetadata;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Gauge;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperationalReadModelService {
    public static final String CONSUMER = "operations-read-model-v1";
    private final JdbcTemplate jdbc;
    private final MeterRegistry metrics;
    private final Clock clock;

    public OperationalReadModelService(JdbcTemplate jdbc, MeterRegistry metrics, Clock clock) {
        this.jdbc = jdbc;
        this.metrics = metrics;
        this.clock = clock;
        Gauge.builder("operations_open_incidents", jdbc, source -> {
            Long value = source.queryForObject("SELECT count(*) FROM operational_incident WHERE resolved=false", Long.class);
            return value == null ? 0 : value;
        }).register(metrics);
    }

    @Transactional
    public void count(EventMetadata metadata, String metricName, String dimension) {
        if (!remember(metadata)) return;
        increment(metadata, metricName, dimension, 1, null);
    }

    @Transactional
    public void amount(EventMetadata metadata, String countMetric, String volumeMetric,
            String countDimension, String currency, BigDecimal amount) {
        if (!remember(metadata)) return;
        increment(metadata, countMetric, countDimension, 1, null);
        increment(metadata, volumeMetric, "currency=" + safe(currency), 0, amount);
    }

    @Transactional
    public void failure(EventMetadata metadata, String metricName, String source,
            String incidentType, String reason, String dimension, String severity) {
        if (!remember(metadata)) return;
        increment(metadata, metricName, dimension, 1, null);
        jdbc.update("""
                INSERT INTO operational_incident(id,incident_type,severity,source_service,
                aggregate_type,aggregate_id,reason_code,correlation_id,occurred_at,resolved,created_at)
                VALUES (?,?,?,?,?,?,?,?,?,false,?)
                """, UUID.randomUUID(), incidentType, severity, source,
                metadata.aggregateType(), metadata.aggregateId(), safe(reason),
                metadata.correlationId(), timestamp(metadata.occurredAt()), timestamp(Instant.now(clock)));
        metrics.counter("operations_open_incidents_created_total", "type", incidentType).increment();
    }

    private boolean remember(EventMetadata metadata) {
        int inserted = jdbc.update("""
                INSERT INTO processed_event(event_id,consumer_name,event_type,aggregate_id,processed_at,correlation_id)
                VALUES (?,?,?,?,?,?) ON CONFLICT DO NOTHING
                """, metadata.eventId(), CONSUMER, metadata.eventType().name(),
                metadata.aggregateId(), timestamp(Instant.now(clock)), metadata.correlationId());
        if (inserted == 0) {
            metrics.counter("operations_duplicate_events_total",
                    "eventType", metadata.eventType().name()).increment();
            return false;
        }
        metrics.counter("operations_event_processing_total",
                "eventType", metadata.eventType().name()).increment();
        return true;
    }

    private void increment(EventMetadata metadata, String name, String dimension,
            long count, BigDecimal amount) {
        LocalDate day = metadata.occurredAt().atZone(ZoneOffset.UTC).toLocalDate();
        jdbc.update("""
                INSERT INTO daily_operational_metric(id,metric_date,metric_name,dimension,count_value,decimal_value,updated_at)
                VALUES (?,?,?,?,?,?,?)
                ON CONFLICT(metric_date,metric_name,dimension) DO UPDATE SET
                  count_value=daily_operational_metric.count_value+EXCLUDED.count_value,
                  decimal_value=COALESCE(daily_operational_metric.decimal_value,0)+COALESCE(EXCLUDED.decimal_value,0),
                  updated_at=EXCLUDED.updated_at,version=daily_operational_metric.version+1
                """, UUID.randomUUID(), Date.valueOf(day), name, safe(dimension), count,
                amount, timestamp(Instant.now(clock)));
    }

    private Timestamp timestamp(Instant instant) {
        return Timestamp.from(instant);
    }

    private String safe(String value) {
        String result = value == null || value.isBlank() ? "UNKNOWN" : value;
        return result.substring(0, Math.min(80, result.length()));
    }
}
