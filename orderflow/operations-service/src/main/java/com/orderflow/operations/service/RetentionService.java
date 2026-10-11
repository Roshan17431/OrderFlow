package com.orderflow.operations.service;

import com.orderflow.operations.config.SchedulerProperties;
import com.orderflow.operations.scheduler.JobResult;
import com.orderflow.operations.scheduler.SchedulerLeaseService;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.sql.Timestamp;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class RetentionService {
    private final JdbcTemplate jdbc;
    private final SchedulerProperties properties;
    private final SchedulerLeaseService leases;
    private final MeterRegistry metrics;
    private final Clock clock;

    public RetentionService(JdbcTemplate jdbc, SchedulerProperties properties,
            SchedulerLeaseService leases, MeterRegistry metrics, Clock clock) {
        this.jdbc = jdbc; this.properties = properties; this.leases = leases;
        this.metrics = metrics; this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.scheduler.retention-delay:24h}")
    public void cleanup() {
        leases.execute("operationsRetentionJob", properties.retentionLockDuration(), this::deleteEligible);
    }

    JobResult deleteEligible() {
        int processed = boundedDelete("processed_event", "processed_at", Instant.now(clock).minus(properties.processedEventRetention()));
        processed += boundedCompletedRuns(Instant.now(clock).minus(properties.jobHistoryRetention()));
        metrics.counter("retention_cleanup_records_total", "service", "operations-service").increment(processed);
        return JobResult.success(processed);
    }

    private int boundedDelete(String table, String timestampColumn, Instant cutoff) {
        return jdbc.update("DELETE FROM " + table + " WHERE ctid IN (SELECT ctid FROM " + table
                + " WHERE " + timestampColumn + " < ? LIMIT ?)", Timestamp.from(cutoff), properties.retentionBatchSize());
    }

    private int boundedCompletedRuns(Instant cutoff) {
        return jdbc.update("DELETE FROM scheduled_job_run WHERE id IN (SELECT id FROM scheduled_job_run WHERE completed_at < ? AND status <> 'RUNNING' LIMIT ?)",
                Timestamp.from(cutoff), properties.retentionBatchSize());
    }
}
