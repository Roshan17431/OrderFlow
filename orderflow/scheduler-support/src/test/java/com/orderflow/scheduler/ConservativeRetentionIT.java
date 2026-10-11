package com.orderflow.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@Testcontainers
class ConservativeRetentionIT {
    @Container static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:16-alpine");
    @Test void cutoffsBindToPostgresAndActiveRecordsSurviveCleanup() {
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword()));
        jdbc.execute("create table processed_event(processed_at timestamptz)");
        jdbc.execute("create table idempotency_record(completed_at timestamptz)");
        jdbc.execute("create table outbox_event(status varchar, published_at timestamptz)");
        jdbc.execute("create table scheduled_job_run(status varchar, completed_at timestamptz)");
        jdbc.update("insert into processed_event values (now()-interval '100 days'), (now())");
        jdbc.update("insert into idempotency_record values (now()-interval '100 days'), (null), (now())");
        jdbc.update("insert into outbox_event values ('PUBLISHED', now()-interval '100 days'), ('NEW', now()-interval '100 days')");
        jdbc.update("insert into scheduled_job_run values ('SUCCEEDED', now()-interval '100 days'), ('RUNNING', now()-interval '100 days')");
        var metrics = new SimpleMeterRegistry();
        var properties = new RetentionProperties(Duration.ofDays(1), Duration.ofMinutes(10), Duration.ofDays(90), Duration.ofDays(90), Duration.ofDays(30), Duration.ofDays(30), 1000);
        var job = new ConservativeRetentionJob(jdbc, null, properties, metrics, Clock.systemUTC());
        assertThat(job.clean().processedCount()).isEqualTo(4);
        assertThat(jdbc.queryForObject("select count(*) from idempotency_record", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select status from outbox_event", String.class)).isEqualTo("NEW");
        assertThat(jdbc.queryForObject("select status from scheduled_job_run", String.class)).isEqualTo("RUNNING");
    }
}
