package com.orderflow.scheduler;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

public class PostgresJobScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(PostgresJobScheduler.class);
    private final JdbcTemplate jdbc;
    private final SchedulerSupportProperties properties;
    private final MeterRegistry metrics;
    private final Clock clock;
    private final String service;
    private final ConcurrentHashMap<String, AtomicLong> lastSuccess = new ConcurrentHashMap<>();

    public PostgresJobScheduler(JdbcTemplate jdbc, SchedulerSupportProperties properties,
            MeterRegistry metrics, Clock clock, String service) {
        this.jdbc = jdbc; this.properties = properties; this.metrics = metrics;
        this.clock = clock; this.service = service;
    }

    public boolean execute(String job, Duration lease, Function<String, JobResult> task) {
        Instant started = Instant.now(clock);
        String correlation = "scheduled-" + UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        metrics.counter("scheduler_job_runs_total", "job", job, "service", service, "result", "attempted").increment();
        if (!acquire(job, lease, started)) {
            jdbc.update("INSERT INTO scheduled_job_run(id,job_name,instance_id,started_at,completed_at,status,duration_ms,correlation_id) VALUES (?,?,?,?,?,'SKIPPED_LOCKED',0,?)",
                    runId, job, properties.instanceId(), timestamp(started), timestamp(started), correlation);
            metrics.counter("scheduler_job_skipped_locked_total", "job", job, "service", service).increment();
            return false;
        }
        jdbc.update("INSERT INTO scheduled_job_run(id,job_name,instance_id,started_at,status,correlation_id) VALUES (?,?,?,?, 'RUNNING',?)",
                runId, job, properties.instanceId(), timestamp(started), correlation);
        Timer.Sample timer = Timer.start(metrics);
        JobResult result = JobResult.success(0);
        String status = "SUCCEEDED";
        String error = null;
        try {
            result = task.apply(correlation);
            status = result.failureCount() == 0 ? "SUCCEEDED" : "PARTIAL";
            metrics.counter("scheduler_job_success_total", "job", job, "service", service).increment();
            lastSuccess.computeIfAbsent(job, key -> {
                AtomicLong value = new AtomicLong();
                metrics.gauge("scheduler_job_last_success_timestamp",
                        java.util.List.of(io.micrometer.core.instrument.Tag.of("job", key),
                                io.micrometer.core.instrument.Tag.of("service", service)), value);
                return value;
            }).set(Instant.now(clock).getEpochSecond());
            return true;
        } catch (RuntimeException exception) {
            status = "FAILED";
            error = safe(exception.getClass().getSimpleName());
            result = new JobResult(0, 1);
            metrics.counter("scheduler_job_failure_total", "job", job, "service", service).increment();
            throw exception;
        } finally {
            Instant completed = Instant.now(clock);
            long duration = Duration.between(started, completed).toMillis();
            timer.stop(metrics.timer("scheduler_job_duration_seconds", "job", job, "service", service));
            jdbc.update("UPDATE scheduled_job_run SET completed_at=?,status=?,processed_count=?,failure_count=?,duration_ms=?,error_code=? WHERE id=?",
                    timestamp(completed), status, result.processedCount(), result.failureCount(), duration, error, runId);
            jdbc.update("UPDATE scheduled_job_lock SET locked_until=?,last_completed_at=?,last_status=?,last_duration_ms=?,failure_count=failure_count+?,last_error_code=?,updated_at=? WHERE job_name=? AND locked_by=?",
                    timestamp(completed), timestamp(completed), status, duration,
                    "FAILED".equals(status) ? 1 : 0, error,
                    timestamp(completed), job, properties.instanceId());
            LOGGER.info("event=scheduled_job service={} job={} status={} processedCount={} failureCount={} durationMs={} correlationId={}",
                    service, job, status, result.processedCount(), result.failureCount(), duration, correlation);
        }
    }

    public boolean acquire(String job, Duration lease, Instant now) {
        return !jdbc.query("""
                INSERT INTO scheduled_job_lock(job_name,locked_until,locked_at,locked_by,last_started_at,updated_at)
                VALUES (?,?,?,?,?,?)
                ON CONFLICT(job_name) DO UPDATE SET locked_until=EXCLUDED.locked_until,
                  locked_at=EXCLUDED.locked_at,locked_by=EXCLUDED.locked_by,
                  last_started_at=EXCLUDED.last_started_at,updated_at=EXCLUDED.updated_at
                WHERE scheduled_job_lock.locked_until <= EXCLUDED.locked_at RETURNING job_name
                """, (rs, row) -> rs.getString(1), job, timestamp(now.plus(lease)), timestamp(now),
                properties.instanceId(), timestamp(now), timestamp(now)).isEmpty();
    }

    private Timestamp timestamp(Instant instant) {
        return Timestamp.from(instant);
    }

    private String safe(String value) {
        String code = value.toUpperCase(Locale.ROOT);
        return code.substring(0, Math.min(80, code.length()));
    }
}
