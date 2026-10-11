package com.orderflow.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class PostgresJobSchedulerTest {
    @Test void lockedInstanceSkipsTaskAndRecordsHistory() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PostgresJobScheduler scheduler = spy(new PostgresJobScheduler(jdbc,
                new SchedulerSupportProperties("instance-a"), new SimpleMeterRegistry(),
                Clock.fixed(Instant.parse("2026-08-19T00:00:00Z"), ZoneOffset.UTC), "test-service"));
        doReturn(false).when(scheduler).acquire(eq("job"), eq(Duration.ofMinutes(1)), any(Instant.class));
        AtomicBoolean called = new AtomicBoolean();
        assertThat(scheduler.execute("job", Duration.ofMinutes(1), correlation -> {
            called.set(true); return JobResult.success(1);
        })).isFalse();
        assertThat(called).isFalse();
        verify(jdbc).update(any(String.class), any(Object[].class));
    }

    @Test void winningInstanceRunsBoundedTaskAndCompletesHistory() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        PostgresJobScheduler scheduler = spy(new PostgresJobScheduler(jdbc,
                new SchedulerSupportProperties("instance-a"), meters,
                Clock.fixed(Instant.parse("2026-08-19T00:00:00Z"), ZoneOffset.UTC), "test-service"));
        doReturn(true).when(scheduler).acquire(eq("job"), eq(Duration.ofMinutes(1)), any(Instant.class));
        assertThat(scheduler.execute("job", Duration.ofMinutes(1), correlation -> JobResult.success(3))).isTrue();
        assertThat(meters.counter("scheduler_job_success_total", "job", "job", "service", "test-service").count()).isEqualTo(1);
    }
}
