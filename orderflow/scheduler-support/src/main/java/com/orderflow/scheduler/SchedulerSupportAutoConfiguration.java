package com.orderflow.scheduler;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

@AutoConfiguration
@ConditionalOnClass(JdbcTemplate.class)
@EnableConfigurationProperties({SchedulerSupportProperties.class, RetentionProperties.class, OutboxMetricsProperties.class})
public class SchedulerSupportAutoConfiguration {
    @Bean @ConditionalOnMissingBean
    PostgresJobScheduler postgresJobScheduler(JdbcTemplate jdbc, SchedulerSupportProperties properties,
            MeterRegistry metrics, Clock clock, Environment environment) {
        return new PostgresJobScheduler(jdbc, properties, metrics, clock,
                environment.getProperty("spring.application.name", "unknown-service"));
    }

    @Bean @ConditionalOnMissingBean
    ConservativeRetentionJob conservativeRetentionJob(JdbcTemplate jdbc, PostgresJobScheduler scheduler,
            RetentionProperties properties, MeterRegistry metrics, Clock clock) {
        return new ConservativeRetentionJob(jdbc, scheduler, properties, metrics, clock);
    }

    @Bean @ConditionalOnMissingBean
    OutboxReliabilityMetrics outboxReliabilityMetrics(JdbcTemplate jdbc, MeterRegistry metrics,
            Environment environment, OutboxMetricsProperties properties) {
        return new OutboxReliabilityMetrics(jdbc, metrics,
                environment.getProperty("spring.application.name", "unknown-service"), properties);
    }
}
