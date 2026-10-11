package com.orderflow.operations.scheduler;

import com.orderflow.operations.config.SchedulerProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class SchedulerLeaseService {
    private static final Logger LOGGER = LoggerFactory.getLogger(SchedulerLeaseService.class);
    private final JdbcTemplate jdbc;
    private final SchedulerProperties properties;
    private final MeterRegistry metrics;
    private final Clock clock;
    private final ConcurrentHashMap<String, AtomicLong> lastSuccess = new ConcurrentHashMap<>();

    public SchedulerLeaseService(JdbcTemplate jdbc, SchedulerProperties properties,
            MeterRegistry metrics, Clock clock) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.metrics = metrics;
        this.clock = clock;
    }

    public boolean execute(String job, Duration lease, Supplier<JobResult> task) {
        Instant started = Instant.now(clock);
        String correlation = "scheduled-" + UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        metrics.counter("scheduler_job_runs_total", "job", job,
                "service", "operations-service", "result", "attempted").increment();
        if (!acquire(job, lease, started)) {
            recordSkipped(runId, job, started, correlation);
            metrics.counter("scheduler_job_skipped_locked_total", "job", job,
                    "service", "operations-service").increment();
            return false;
        }
        jdbc.update("INSERT INTO scheduled_job_run(id,job_name,instance_id,started_at,status,correlation_id) VALUES (?,?,?,?,?,?)",
                runId, job, properties.instanceId(), timestamp(started), "RUNNING", correlation);
        Timer.Sample sample = Timer.start(metrics);
        JobResult result = new JobResult(0, 0);
        String status = "SUCCEEDED";
        String error = null;
        try {
            result = task.get();
            status = result.failureCount() == 0 ? "SUCCEEDED" : "PARTIAL";
            metrics.counter("scheduler_job_success_total", "job", job,
                    "service", "operations-service").increment();
            lastSuccess.computeIfAbsent(job, key -> {
                AtomicLong value = new AtomicLong();
                metrics.gauge("scheduler_job_last_success_timestamp",
                        java.util.List.of(io.micrometer.core.instrument.Tag.of("job", key),
                                io.micrometer.core.instrument.Tag.of("service", "operations-service")), value);
                return value;
            }).set(Instant.now(clock).getEpochSecond());
            return true;
        } catch (RuntimeException exception) {
            status = "FAILED";
            error = safeCode(exception);
            result = new JobResult(0, 1);
            metrics.counter("scheduler_job_failure_total", "job", job,
                    "service", "operations-service").increment();
            throw exception;
        } finally {
            long duration = Duration.between(started, Instant.now(clock)).toMillis();
            sample.stop(metrics.timer("scheduler_job_duration_seconds", "job", job,
                    "service", "operations-service"));
            complete(runId, job, status, result, duration, error);
            LOGGER.info("event=scheduled_job job={} status={} processedCount={} failureCount={} durationMs={} correlationId={}",
                    job, status, result.processedCount(), result.failureCount(), duration, correlation);
        }
    }

    boolean acquire(String job, Duration lease, Instant now) {
        try {
            return !jdbc.query("""
                    INSERT INTO scheduled_job_lock(job_name,locked_until,locked_at,locked_by,last_started_at,updated_at)
                    VALUES (?,?,?,?,?,?)
                    ON CONFLICT(job_name) DO UPDATE SET
                      locked_until=EXCLUDED.locked_until,locked_at=EXCLUDED.locked_at,
                      locked_by=EXCLUDED.locked_by,last_started_at=EXCLUDED.last_started_at,
                      updated_at=EXCLUDED.updated_at
                    WHERE scheduled_job_lock.locked_until <= EXCLUDED.locked_at
                    RETURNING job_name
                    """, (rs, row) -> rs.getString(1), job, timestamp(now.plus(lease)),
                    timestamp(now), properties.instanceId(), timestamp(now), timestamp(now)).isEmpty();
        } catch (DataAccessException exception) {
            throw new IllegalStateException("SCHEDULER_LOCK_ACQUISITION_FAILED", exception);
        }
    }

    private void recordSkipped(UUID id, String job, Instant at, String correlation) {
        jdbc.update("INSERT INTO scheduled_job_run(id,job_name,instance_id,started_at,completed_at,status,duration_ms,correlation_id) VALUES (?,?,?,?,?,'SKIPPED_LOCKED',0,?)",
                id, job, properties.instanceId(), timestamp(at), timestamp(at), correlation);
    }

    private void complete(UUID id, String job, String status, JobResult result,
            long duration, String error) {
        Instant completed = Instant.now(clock);
        jdbc.update("UPDATE scheduled_job_run SET completed_at=?,status=?,processed_count=?,failure_count=?,duration_ms=?,error_code=? WHERE id=?",
                timestamp(completed), status, result.processedCount(), result.failureCount(), duration, error, id);
        jdbc.update("""
                UPDATE scheduled_job_lock SET locked_until=?,last_completed_at=?,last_status=?,
                last_duration_ms=?,failure_count=failure_count+?,last_error_code=?,updated_at=?
                WHERE job_name=? AND locked_by=?
                """, timestamp(completed), timestamp(completed), status, duration,
                "FAILED".equals(status) ? 1 : 0, error, timestamp(completed), job, properties.instanceId());
    }

    private Timestamp timestamp(Instant instant) {
        return Timestamp.from(instant);
    }

    private String safeCode(RuntimeException exception) {
        String value = exception.getClass().getSimpleName().toUpperCase(java.util.Locale.ROOT);
        return value.substring(0, Math.min(80, value.length()));
    }
}
