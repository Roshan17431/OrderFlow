package com.orderflow.operations.config;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfiguration {
    @Bean HealthIndicator kafkaHealthIndicator(KafkaAdmin kafkaAdmin) {
        Map<String, Object> configuration = kafkaAdmin.getConfigurationProperties();
        return () -> {
            AdminClient client = AdminClient.create(configuration);
            try {
                client.describeCluster().clusterId().get(3, TimeUnit.SECONDS);
                return Health.up().build();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return Health.down().build();
            } catch (Exception exception) {
                return Health.down().build();
            } finally {
                client.close(java.time.Duration.ZERO);
            }
        };
    }

    @Bean NewTopic operationsDlt(KafkaTopicProperties topics) {
        return TopicBuilder.name(topics.operationsDlt()).partitions(3).replicas(1).build();
    }

    @Bean DefaultErrorHandler operationsKafkaErrorHandler(
            KafkaTemplate<String, String> kafka, KafkaTopicProperties topics, MeterRegistry metrics) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafka,
                (record, exception) -> {
                    metrics.counter("operations_event_processing_failures_total").increment();
                    metrics.counter("orderflow_kafka_dlt_publications_total",
                            "service", "operations-service").increment();
                    return new TopicPartition(topics.operationsDlt(), record.partition());
                });
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, new FixedBackOff(500, 2));
        handler.setCommitRecovered(true);
        return handler;
    }
}
